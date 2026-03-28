# Switch-Admin 项目开发技能

## 核心教训总结

### 问题根因分析（维护模块 & 网络模块）

**现象**：菜单代码已写，但浏览器不显示

**根本原因**：
1. **服务器未真正重启** - 代码修改后，旧服务器进程仍在运行，新代码未生效
2. **Bootstrap 函数执行时机** - 菜单初始化只在首次启动时执行，已存在的菜单会跳过
3. **验证不充分** - 没有在代码修改后立即验证数据库状态和服务器日志
4. **重复犯错** - 网络模块的问题在维护模块重演

**解决过程**：
- 第 1 次：修改 InitMenu 检查逻辑 → 用户反馈菜单仍没有 → 服务器没重启
- 第 2 次：添加 InitMaintenanceMenu 独立函数 → 用户反馈仍没有 → 服务器没重启
- 第 3 次：创建 check_menu.go 查数据库 → 发现菜单确实没创建 → 定位到服务器进程问题
- 最终：杀掉旧进程 (28340) → 重启服务器 → 菜单出现 (ID=16)

---

## 标准化开发流程（必须遵守）

### Phase 1: 代码修改后

```
□ 1. 保存所有修改的文件
□ 2. 执行 `go build` 验证编译通过
□ 3. 检查是否有语法错误或未使用变量
```

### Phase 2: 服务器重启（关键！）

```
□ 1. 查找旧进程：netstat -ano | findstr :9033
□ 2. 杀掉旧进程：wmic process where "ProcessId=XXX" delete
□ 3. 验证进程已终止：netstat -ano | findstr :9033（应无输出）
□ 4. 启动新服务器：go run ./cmd/main.go &
□ 5. 等待 3 秒，验证新进程：netstat -ano | findstr :9033 | findstr LISTENING
```

### Phase 3: 功能验证

```
□ 1. 查看启动日志，确认看到预期的 "插入 XXX 菜单" 日志
□ 2. 运行检查工具：go run ./cmd/check_menu.go
□ 3. 验证数据库中有所需的菜单记录
□ 4. 打开浏览器访问 http://localhost:9033/admin 查看菜单
□ 5. 测试 API 端点：curl http://localhost:9033/api/v1/xxx
```

---

## 菜单创建标准流程

### 步骤 1: 在 bootstrap.go 中添加菜单初始化

```go
func InitMenu(conn db.Connection) {
    // 检查主菜单是否已存在
    menuExists, _ := conn.Query("SELECT id FROM goadmin_menu WHERE title = '菜单名称' LIMIT 1")

    var menuId int64
    if menuExists == nil || len(menuExists) == 0 {
        // 插入主菜单
        menuResult, _ := conn.Exec("INSERT INTO goadmin_menu (parent_id, type, title, uri, icon, `order`) VALUES (0, 0, '菜单名称', '', 'fa fa-icon', 排序)")
        menuId, _ = menuResult.LastInsertId()
        log.Println("插入 XXX 主菜单")
    } else {
        menuId = menuExists[0]["id"].(int64)
    }

    // 插入子菜单
    subMenus := []struct {
        name  string
        uri   string
        icon  string
        order int
    }{
        {"子菜单 1", "/module/path1", "fa fa-icon1", 1},
        {"子菜单 2", "/module/path2", "fa fa-icon2", 2},
    }

    for _, menu := range subMenus {
        exists, _ := conn.Query("SELECT id FROM goadmin_menu WHERE title = ? AND parent_id = ? LIMIT 1", menu.name, menuId)
        if exists != nil && len(exists) > 0 {
            continue
        }
        conn.Exec("INSERT INTO goadmin_menu (parent_id, type, title, uri, icon, `order`) VALUES (?, 1, ?, ?, ?, ?)",
            menuId, menu.name, menu.uri, menu.icon, menu.order)
        log.Printf("插入菜单：%s (parent_id=%d)", menu.name, menuId)
    }

    // 添加角色菜单关联
    adminRoleId := int64(1)
    conn.Exec("INSERT OR IGNORE INTO goadmin_role_menu (role_id, menu_id) VALUES (?, ?)", adminRoleId, menuId)

    // 添加子菜单关联
    subMenusResult, _ := conn.Query("SELECT id FROM goadmin_menu WHERE parent_id = ?", menuId)
    for _, row := range subMenusResult {
        subMenuId := int(row["id"].(int64))
        conn.Exec("INSERT OR IGNORE INTO goadmin_role_menu (role_id, menu_id) VALUES (?, ?)", adminRoleId, subMenuId)
    }

    log.Println("XXX 菜单初始化完成")
}
```

### 步骤 2: 在 Bootstrap 函数中调用

```go
func Bootstrap(e *engine.Engine) {
    connection := e.SqliteConnection()
    InitDatabaseTables(connection)
    InitMenu(connection)
    InitDashboard(connection)
    InitNewModuleMenu(connection) // 新增模块的初始化函数
}
```

### 步骤 3: 在 main.go 中注册页面路由

```go
// 新增模块页面路由
e.HTML("GET", "/admin/module/path1", datamodel.GetModulePath1Content, false)
e.HTML("GET", "/admin/module/path2", datamodel.GetModulePath2Content, false)
```

### 步骤 4: 在 main.go 中注册 API 路由

```go
newModuleHandler := handler.NewModuleHandler()
r.GET("/api/v1/module/data", newModuleHandler.GetData)
r.POST("/api/v1/module/action", newModuleHandler.DoAction)
```

---

## 验证命令清单

### 验证菜单是否在数据库中

```bash
go run ./cmd/check_menu.go
# 查看输出中是否有预期的菜单记录
```

### 验证服务器进程

```bash
# 查看端口占用
netstat -ano | findstr :9033

# 杀掉进程
wmic process where "ProcessId=XXX" delete

# 验证已终止
netstat -ano | findstr :9033
```

### 验证服务器日志

```bash
# 查看启动日志中是否有 "插入 XXX 菜单" 的信息
# 或查看 server.log 文件
```

### 测试 API 端点

```bash
# 测试 API 是否正常响应
curl -s "http://localhost:9033/api/v1/xxx"
```

### 浏览器验证

```
1. 访问 http://localhost:9033/admin 查看左侧菜单
2. 访问 http://localhost:9033/admin/menu?_pjax=%23pjax-container 查看菜单管理
```

---

## 错误处理与调试

### 常见问题排查

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| 菜单不显示 | 服务器未重启 | 杀掉旧进程，重启服务器 |
| 菜单不显示 | 数据库已有记录跳过插入 | 检查 check_menu.go 输出，确认菜单是否存在 |
| 菜单不显示 | 角色菜单关联缺失 | 确保插入 goadmin_role_menu 表 |
| API 404 | 路由未注册 | 检查 main.go 中是否注册了路由 |
| API 500 | 处理器代码错误 | 查看服务器日志，修复代码后重启 |

### 日志检查点

服务器启动后，必须看到以下日志：
- `菜单初始化完成`
- `插入 XXX 主菜单`（如果是首次创建）
- `=== GoAdmin 启动完成 ===`
- `Admin UI: http://localhost:9033/admin`

---

## 配置模块开发注意事项（后续模块）

### 提前准备

1. **菜单命名** - 确保不与现有菜单重复
2. **URI 路径** - 使用 `/module/path` 格式
3. **Icon 选择** - 使用 FontAwesome 图标
4. **权限控制** - 确保角色菜单关联正确

### 开发顺序

1. 先在 bootstrap.go 添加菜单初始化代码
2. 在 main.go 注册页面路由
3. 创建 datamodel 页面处理器
4. 创建 handler API 处理器
5. 在 main.go 注册 API 路由
6. **重启服务器验证**
7. 测试功能

### 禁止行为

- ❌ 代码修改后直接刷新浏览器（必须重启服务器）
- ❌ 看到 "GoAdmin 启动成功" 就认为完成（必须验证菜单）
- ❌ 假设代码正确就不验证（必须运行 check_menu.go）
- ❌ 不查看日志就继续开发（必须确认初始化成功）

---

## 项目结构参考

```
switch-admin/
├── cmd/
│   ├── main.go              # 主入口：路由注册、服务器启动
│   └── check_menu.go        # 菜单检查工具
├── internal/
│   ├── datamodel/
│   │   ├── bootstrap.go     # 菜单初始化（核心！）
│   │   ├── maintenance.go   # 维护模块页面处理器
│   │   ├── network.go       # 网络模块页面处理器
│   │   └── config.go        # 配置模块页面处理器（待创建）
│   ├── handler/
│   │   ├── maintenance_handler.go  # 维护模块 API
│   │   ├── route_handler.go        # 网络模块 API
│   │   └── config_handler.go       # 配置模块 API（待创建）
│   └── dao/
│       └── config_dao.go    # 数据访问层
├── data/
│   ├── admin.db             # SQLite 数据库
│   └── init.sql             # 初始化 SQL 脚本
└── uploads/                 # 上传文件目录
```

---

## 快速复制模板

### bootstrap.go 菜单初始化模板

```go
func InitXxxMenu(conn db.Connection) {
    xxxExists, _ := conn.Query("SELECT id FROM goadmin_menu WHERE title = 'XXX 模块' LIMIT 1")

    var xxxId int64
    if xxxExists == nil || len(xxxExists) == 0 {
        xxxResult, _ := conn.Exec("INSERT INTO goadmin_menu (parent_id, type, title, uri, icon, `order`) VALUES (0, 0, 'XXX 模块', '', 'fa fa-xxx', 排序)")
        xxxId, _ = xxxResult.LastInsertId()
        log.Println("插入 XXX 模块主菜单")
    } else {
        xxxId = xxxExists[0]["id"].(int64)
    }

    // 子菜单...

    // 角色关联...

    log.Println("XXX 模块菜单初始化完成")
}
```

### handler.go API 模板

```go
type XxxHandler struct {
    configDAO *dao.ConfigDAO
}

func NewXxxHandler() *XxxHandler {
    return &XxxHandler{
        configDAO: dao.NewConfigDAO(),
    }
}

func (h *XxxHandler) GetData(c *gin.Context) {
    c.JSON(http.StatusOK, gin.H{
        "code": 200,
        "data": gin.H{
            // 数据
        },
    })
}
```

---

## 执行检查清单（每次开发新模块）

开发前：
- [ ] 确认没有正在运行的服务器进程
- [ ] 确认数据库状态（运行 check_menu.go）

开发中：
- [ ] 代码修改后执行 `go build` 验证编译
- [ ] 确保 bootstrap.go 中调用了新的初始化函数

开发后：
- [ ] **杀掉旧服务器进程**
- [ ] **启动新服务器**
- [ ] **查看启动日志**
- [ ] **运行 check_menu.go 验证菜单**
- [ ] **浏览器访问验证**
- [ ] **测试 API 端点**

---

**最后提醒**：每次修改代码后，必须重启服务器！这是最重要的教训。
