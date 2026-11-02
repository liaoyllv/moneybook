# 记一笔（MoneyBook）UML 建模

> 文档版本：v2.1 ｜ 对应软件版本：记一笔 v2.1.21（最新发布版） ｜ 更新日期：2026-11-02 ｜ 代码仓库：https://github.com/liaoyllv/moneybook
> 图表使用 Mermaid 规范绘制（支持 GitHub 原生高质量渲染）。

## 1. 系统用例图

### 1.1 总体用例全景图

```mermaid
flowchart LR
    U((用户))
    subgraph 记一笔系统 (v2.1.21)
        UC1[记一笔极速记账]
        UC2[首页账单整合浏览与检索]
        UC3[四维单屏穿透统计分析]
        UC4[多账本与分类账户管理]
        UC5[双层预算设置与超支预警]
        UC6[数据安全与外部账单迁移]
        UC7[交互反馈与外观设置]
        UC8[桌面快捷小组件]
    end

    U --> UC1
    U --> UC2
    U --> UC3
    U --> UC4
    U --> UC5
    U --> UC6
    U --> UC7
    U --> UC8

    UC1 -.->|include| UC11{{分类3x4高频选择}}
    UC1 -.->|include| UC12{{标签多选与联想输入}}
    UC1 -.->|extend| UC13{{附加相册图片/文件}}
    UC1 -.->|extend| UC14{{连续记账模式}}
    UC2 -.->|include| UC21{{关键词全局搜索}}
    UC2 -.->|include| UC22{{分类/账户/金额筛选}}
    UC3 -.->|include| UC31{{占比Donut图与明细展开}}
    UC3 -.->|include| UC32{{当月上月同日对称对比}}
    UC3 -.->|include| UC33{{110dp趋势折线图与KPI}}
    UC3 -.->|include| UC34{{日历月度概览与补账}}
    UC4 -.->|extend| UC41{{分类合并与预算累加}}
    UC4 -.->|extend| UC42{{账本重命名与账户备注}}
    UC6 -.->|extend| UC61{{鲨鱼记账等全量导入}}
    UC6 -.->|include| UC62{{AES-256 加密备份}}
    UC7 -.->|include| UC71{{清脆音效与触感开关}}
    UC7 -.->|include| UC72{{暖色调与深色主题切换}}
```

---

## 2. 核心交互时序图

### 2.1 记一笔极速记账与标签即时联想

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant S as AddEditBillScreen
    participant VM as AddEditBillViewModel
    participant SF as SoundFeedback
    participant R as BillRepository
    participant D as Room (SQLite)
    participant T as TopSnackbarManager

    U->>S: 点击首页悬浮「+」按钮
    S->>VM: 初始化（默认当天日期、前置高频分类、默认支付宝）
    S-->>U: 秒开呈现 3x4 紧凑网格
    U->>S: 点击分类图标（如「餐饮」）
    S->>SF: 播放 44.1kHz 清脆触击音效 + 微触感
    S->>VM: 加载餐饮分类关联历史标签
    VM-->>S: 候选标签展开展现
    U->>S: 行末输入框键入「外卖」
    S->>VM: 联想匹配已用标签
    U->>S: 确认输入金额与标签
    U->>S: 点击「记完了」
    S->>SF: 播放保存确认音效
    S->>VM: saveBill(amount, category, tags, note)
    VM->>R: insertBill(billEntity)
    R->>D: 写入 SQLite 数据库
    D-->>R: 成功，返回 rowId
    VM->>T: 发送顶部通知「记账成功」
    VM-->>S: 触发导航返回事件
    S-->>U: 平滑返回首页，顶部胶囊浮现「记账成功」
```

### 2.2 全局顶部胶囊通知调度机制

```mermaid
sequenceDiagram
    autonumber
    participant Page as 各业务 Screen (如 BudgetScreen)
    participant VM as 业务 ViewModel
    participant TM as TopSnackbarManager
    participant Host as TopSnackbarHost
    participant UI as 状态栏下浮动胶囊

    Page->>VM: 用户点击「保存预算」
    VM->>VM: 校验并持久化预算配置
    VM->>TM: show("预算已保存")
    TM->>TM: 封装 SnackbarMessage 并更新 StateFlow
    TM-->>Host: 监听到新消息流
    Host->>UI: 在状态栏正下方滑入圆角半透明胶囊
    Page-->>Page: 自动返回上一页面（输入法收起）
    UI-->>UI: 停留展示 2500ms（完全不被输入法遮挡）
    UI->>UI: 平滑淡出并释放
```

### 2.3 分类合并与预算累加流程

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant CS as CategoryScreen
    participant CVM as CategoryViewModel
    participant CR as CategoryRepository
    participant DB as AppDatabase (withTransaction)
    participant TM as TopSnackbarManager

    U->>CS: 选择源分类 A，点击「合并到...」
    CS->>U: 展示目标分类列表供选择（选择 B）
    U->>CS: 确认合并 A 至 B
    CS->>CVM: mergeCategory(from=A, to=B)
    CVM->>CR: mergeCategories(fromId, toId)
    CR->>DB: 开启数据库事务
    DB->>DB: 更新 bills: categoryId=A 批量变更为 B
    DB->>DB: 查询 category_budgets: 计算同一月份 A+B 预算总和并更新至 B，清理 A 预算
    DB->>DB: 删除源分类 A
    DB-->>CR: 事务提交成功
    CR-->>CVM: 合并完成
    CVM->>TM: show("已将「A」合并至「B」，相关账单与预算已同步更新")
    CVM-->>CS: 刷新分类列表
    CS-->>U: 界面更新完成，顶部弹出成功胶囊
```

### 2.4 外部账单（鲨鱼记账）全量迁移导入

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant S as SettingsScreen
    participant VM as SettingsViewModel
    participant IMP as SharkBillImporter
    participant DB as AppDatabase
    participant TM as TopSnackbarManager

    U->>S: 点击「导入外部账单（鲨鱼记账）」
    S->>U: 唤起 SAF 系统文件选择器
    U->>S: 选取导出 CSV 文件
    S->>VM: importExternalData(uri)
    VM->>IMP: parseAndImport(inputStream)
    IMP->>IMP: 解析 CSV 编码与字段格式（日期、分类、金额、备注）
    IMP->>IMP: 自动映射或补全收支分类与账户
    IMP->>DB: 开启批量插入事务
    DB->>DB: 批量写入账单记录
    DB-->>IMP: 写入完成，统计条数 N
    IMP-->>VM: ImportResult(success=true, count=N)
    VM->>TM: show("成功导入 N 笔历史账单")
    VM-->>S: 提示导入完成，首页与统计即刻呈现全部历史数据
```
