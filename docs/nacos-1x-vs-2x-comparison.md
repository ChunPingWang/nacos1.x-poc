# Nacos 1.x vs 2.x 完整比較

## 目錄

1. [架構概覽](#1-架構概覽)
2. [通訊協議與連線模型](#2-通訊協議與連線模型)
3. [Port 需求](#3-port-需求)
4. [效能比較](#4-效能比較)
5. [配置管理差異](#5-配置管理差異)
6. [服務發現差異](#6-服務發現差異)
7. [認證與授權](#7-認證與授權)
8. [Metadata 模型](#8-metadata-模型)
9. [SDK 差異](#9-sdk-差異)
10. [向下相容性與遷移路徑](#10-向下相容性與遷移路徑)
11. [選擇建議](#11-選擇建議)

---

## 1. 架構概覽

| 面向 | Nacos 1.x | Nacos 2.x |
|------|-----------|-----------|
| 核心架構 | HTTP 短連線 | gRPC 長連線 + HTTP 相容層 |
| 資料一致性 | 自研 Distro + Raft | JRaft (SOFAJRaft) |
| 連線模型 | Request-Response | 雙向長連線 (Bidirectional Streaming) |
| 推送模型 | HTTP Long Polling (配置) / UDP Push (服務) | gRPC Stream Push |
| 外掛機制 | 有限 SPI | 增強 SPI（認證、加密、資料來源） |

### 1.x 架構圖

```
Client ──HTTP──> Nacos Server ──Distro──> Nacos Server (Peer)
       <──Long Poll / UDP──
```

### 2.x 架構圖

```
Client ──gRPC (長連線)──> Nacos Server ──JRaft──> Nacos Server (Peer)
       <──gRPC Push──────
```

---

## 2. 通訊協議與連線模型

### 配置管理

| 特性 | Nacos 1.x | Nacos 2.x |
|------|-----------|-----------|
| 配置查詢 | HTTP GET `/v1/cs/configs` | gRPC `ConfigQueryRequest` |
| 配置發布 | HTTP POST `/v1/cs/configs` | gRPC `ConfigPublishRequest` |
| 變更推送 | HTTP Long Polling (每 30s 一次) | gRPC Stream Push (即時) |
| 推送延遲 | 最長 30 秒（Long Polling 週期） | 毫秒級（長連線推送） |
| 連線數 | 每次請求新建連線 | 單一長連線複用 |

### 服務發現

| 特性 | Nacos 1.x | Nacos 2.x |
|------|-----------|-----------|
| 服務註冊 | HTTP PUT `/v1/ns/instance` | gRPC `InstanceRequest` |
| 心跳維持 | HTTP PUT `/v1/ns/instance/beat` (每 5s) | gRPC 連線本身即心跳 |
| 服務變更推送 | UDP Push（不可靠） | gRPC Stream Push（可靠） |
| 查詢服務 | HTTP GET `/v1/ns/instance/list` | gRPC `ServiceQueryRequest` |

#### 1.x 心跳機制的問題

```
# 1.x: 每個 Instance 每 5 秒發一次 HTTP 心跳
Client ──HTTP PUT /beat──> Server  (每 5s × 每個 Instance)
# 1000 個 Instance = 200 QPS 純心跳請求
```

```
# 2.x: gRPC 連線層自動維持
Client ══gRPC 長連線══> Server  (連線本身即為存活證明)
# 1000 個 Instance = 仍然只有少量連線
```

---

## 3. Port 需求

### Nacos 1.x

| Port | 用途 |
|------|------|
| 8848 | HTTP API（唯一需要的 Port） |

### Nacos 2.x

| Port | 用途 | 計算方式 |
|------|------|----------|
| 8848 | HTTP API（向下相容） | 固定 |
| 9848 | gRPC Client 連線 | 8848 + 1000 |
| 9849 | gRPC Server 間通訊 | 8848 + 1001 |

> **遷移注意**：從 1.x 升級到 2.x 時，需確保防火牆/安全群組額外開放 9848 和 9849 端口。

---

## 4. 效能比較

### 配置推送延遲

| 場景 | Nacos 1.x | Nacos 2.x | 改善幅度 |
|------|-----------|-----------|----------|
| 配置變更通知 | 1-30 秒 (Long Polling) | < 100ms (gRPC Push) | ~99% |
| 配置批量變更 (100 configs) | 分批 Long Poll 返回 | 批量 gRPC Push | ~95% |

### 服務發現延遲

| 場景 | Nacos 1.x | Nacos 2.x | 改善幅度 |
|------|-----------|-----------|----------|
| 實例上線感知 | 1-3 秒 (UDP Push) | < 100ms | ~95% |
| 實例下線感知 | 15-30 秒 (心跳超時) | 秒級 (連線斷開) | ~90% |

### 連線數與資源消耗

| 指標 | Nacos 1.x (1000 Client) | Nacos 2.x (1000 Client) |
|------|--------------------------|--------------------------|
| 連線數 | 每請求新建（高峰數千） | ~1000 長連線 |
| 心跳 QPS | ~200/s (per 1000 instances) | ~0 (gRPC keepalive) |
| Server CPU | 較高（HTTP 解析 + 心跳處理） | 較低（gRPC 高效二進位） |
| Server 記憶體 | 較低 | 略高（維持長連線狀態） |

---

## 5. 配置管理差異

### API 對比

```java
// 1.x 和 2.x 的 Java SDK API 完全相同
ConfigService configService = NacosFactory.createConfigService(properties);
configService.getConfig(dataId, group, timeout);
configService.publishConfig(dataId, group, content);
configService.addListener(dataId, group, listener);
```

### 底層差異

| 特性 | Nacos 1.x | Nacos 2.x |
|------|-----------|-----------|
| Long Polling 週期 | 30 秒 | N/A（使用 gRPC Push） |
| 配置快照 | 本地檔案快照 | 本地檔案快照（相同） |
| 灰度發布 | 基於 IP | 基於 IP + 標籤 |
| 配置加密 | 不支援 | 支援（外掛式） |
| 配置歷史 | 支援（HTTP API） | 支援（增強查詢） |

---

## 6. 服務發現差異

### Ephemeral vs Persistent Instance

| 特性 | Nacos 1.x | Nacos 2.x |
|------|-----------|-----------|
| Ephemeral Instance | Distro 協議同步 | gRPC 連線感知 + Distro |
| Persistent Instance | Raft 協議同步 | JRaft 同步 |
| 健康檢查 (Ephemeral) | Client 主動 HTTP 心跳 | gRPC 連線狀態 |
| 健康檢查 (Persistent) | Server 端 TCP/HTTP 探測 | Server 端探測（相同） |

### 服務訂閱模型

```java
// 1.x: UDP 推送（不可靠，需要 failover polling）
// 底層: Server --UDP--> Client (可能丟包)
//        Client --HTTP Poll--> Server (備援)

// 2.x: gRPC 推送（可靠）
// 底層: Server ==gRPC Stream==> Client (可靠遞送)
```

---

## 7. 認證與授權

| 特性 | Nacos 1.x | Nacos 2.x |
|------|-----------|-----------|
| 認證方式 | Username/Password → JWT Token | 相同 + 增強 |
| 授權模型 | 基本 (Namespace 級別) | RBAC（角色為基礎） |
| Token 傳遞 | HTTP Header/Parameter | gRPC Metadata |
| 外掛認證 | 不支援 | 支援（SPI 外掛） |
| 預設行為 | 預設關閉 Auth | 2.2.1+ 預設建議開啟 |

### 1.x 認證流程

```
1. Client POST /nacos/v1/auth/login (username, password)
2. Server 回傳 accessToken (JWT)
3. Client 後續請求帶上 accessToken 參數
4. Token 過期需重新登入
```

### 2.x 認證流程

```
1. Client 透過 gRPC 連線建立時傳遞 credentials
2. Server 驗證後建立 authenticated session
3. 長連線期間不需重複認證
4. 連線斷開重連時自動重新認證
```

---

## 8. Metadata 模型

### 1.x: 合併式 Metadata

```java
// Instance metadata 包含所有自訂資訊
Instance instance = new Instance();
instance.setIp("10.0.0.1");
instance.setPort(8080);

Map<String, String> metadata = new HashMap<>();
metadata.put("version", "v1");
metadata.put("weight", "100");
metadata.put("env", "prod");
instance.setMetadata(metadata);
```

### 2.x: 分離式 Metadata

```java
// 2.x 引入 ServiceMetadata 和 InstanceMetadata 分層

// Service 級別的 metadata（所有 instance 共享）
// Instance 級別的 metadata（單一 instance 專屬）
// 減少重複資料，更清晰的語義
```

| 面向 | Nacos 1.x | Nacos 2.x |
|------|-----------|-----------|
| Metadata 層級 | Instance 單層 | Service + Instance 雙層 |
| 修改 Metadata | 需重新註冊 Instance | 可獨立更新 Metadata |
| Metadata 傳播 | 跟隨心跳更新 | 獨立 gRPC 更新 |

---

## 9. SDK 差異

### Maven 依賴

```xml
<!-- 1.x SDK -->
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>1.4.6</version>
</dependency>

<!-- 2.x SDK -->
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>2.3.2</version>
    <!-- 自動包含 gRPC 相關依賴 -->
</dependency>
```

### SDK 體積與依賴

| 面向 | nacos-client 1.4.6 | nacos-client 2.3.x |
|------|---------------------|---------------------|
| JAR 大小 | ~400KB | ~600KB |
| 傳遞依賴 | HTTP Client, FastJSON | + gRPC, Protobuf |
| 最低 Java 版本 | Java 8 | Java 8 |

### API 相容性

```java
// 以下 API 在 1.x 和 2.x 完全相同：
ConfigService configService = NacosFactory.createConfigService(properties);
NamingService namingService = NacosFactory.createNamingService(properties);

// ConfigService 方法
configService.getConfig(dataId, group, timeout);
configService.publishConfig(dataId, group, content);
configService.removeConfig(dataId, group);
configService.addListener(dataId, group, listener);
configService.removeListener(dataId, group, listener);

// NamingService 方法
namingService.registerInstance(serviceName, ip, port);
namingService.deregisterInstance(serviceName, ip, port);
namingService.getAllInstances(serviceName);
namingService.selectInstances(serviceName, healthy);
namingService.selectOneHealthyInstance(serviceName);
namingService.subscribe(serviceName, listener);
namingService.unsubscribe(serviceName, listener);
```

> 2.x SDK 完全向下相容 1.x 的 API，遷移時只需更換版本號。

---

## 10. 向下相容性與遷移路徑

### 2.x Server 向下相容

| Client 版本 | Server 1.x | Server 2.x |
|-------------|------------|------------|
| SDK 1.x | HTTP 通訊 | HTTP 通訊（相容層） |
| SDK 2.x | 不支援 | gRPC 通訊 |

> **重要**：Nacos 2.x Server 保留了完整的 HTTP API 相容層，1.x 的 Client 可直接連接 2.x Server。

### 遷移步驟

#### Phase 1: Server 升級（零停機）

```
1. 準備 2.x Server 二進位
2. 滾動升級 Server 節點（一次一個）
3. 確認 2.x Server 叢集正常
4. 開放 9848/9849 端口（給後續 2.x Client 使用）
```

#### Phase 2: Client 逐步遷移

```
1. 更新 Maven 依賴版本 1.4.x → 2.x
2. 確認防火牆開放 gRPC Port
3. 逐服務升級 Client SDK
4. 驗證功能正常（API 完全相容）
```

### 遷移風險

| 風險 | 影響 | 緩解措施 |
|------|------|----------|
| Port 未開放 | 2.x Client 無法連線 | 升級前確認 9848/9849 |
| gRPC 依賴衝突 | 應用啟動失敗 | 檢查 gRPC/Protobuf 版本 |
| 記憶體增加 | Server OOM | 升級前增加記憶體配額 |
| Long Polling → gRPC | 行為差異 | 灰度驗證 |

---

## 11. 選擇建議

### 選擇 Nacos 1.x 的場景

- 生產環境已穩定運行 1.x，無遷移必要
- 網路環境限制只能開放單一 Port (8848)
- 專案使用 gRPC 版本與 Nacos 2.x 依賴衝突
- 配置變更頻率低，Long Polling 延遲可接受
- 服務規模較小（< 100 實例），心跳開銷可忽略

### 選擇 Nacos 2.x 的場景

- 新專案，建議直接使用最新穩定版
- 需要毫秒級配置推送（即時配置生效）
- 大規模服務發現（> 1000 實例），需降低心跳開銷
- 需要 RBAC 細粒度權限控制
- 需要配置加密等進階功能
- 需要更快的實例上下線感知

### 總結對比表

| 維度 | Nacos 1.x | Nacos 2.x | 建議 |
|------|-----------|-----------|------|
| 效能 | 一般 | 優秀 | 2.x |
| 穩定性 | 成熟 | 持續改進中 | 均可 |
| Port 需求 | 簡單 (1個) | 複雜 (3個) | 依環境 |
| 即時性 | 秒級 | 毫秒級 | 2.x |
| 大規模部署 | 受限 | 更適合 | 2.x |
| 遷移成本 | N/A | 低（API 相容） | - |
| 社群支援 | 僅安全修復 | 積極開發中 | 2.x |

> **建議**：新專案使用 Nacos 2.x；既有 1.x 專案根據實際需求評估遷移時機。遷移成本低，主要風險在 Port 和 gRPC 依賴管理。
