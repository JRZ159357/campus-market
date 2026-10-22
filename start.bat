@echo off
setlocal EnableExtensions EnableDelayedExpansion
title 校园二手交易平台 · 一键启动

:: ============================================================
::  校园二手交易平台 v1.0.0  一键启动脚本（Windows）
::  用法：双击运行，或在项目根目录执行  start.bat
::  作用：检查环境 → 启动后端 8080 → 启动管理端 5173 → 输出访问地址
::  说明：脚本使用相对路径，必须在项目根目录运行；会新开两个命令行窗口
:: ============================================================

cd /d "%~dp0"
set "ROOT=%CD%"

call :banner

:: ---------- 步骤 1：解析 JDK 17 ----------
call :step "1/5" "检查 JDK 17 运行环境"
call :resolve_jdk
if not defined JDK_FOUND goto :no_jdk
set "JAVA_HOME=%JDK_FOUND%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call :ok "JAVA_HOME = %JAVA_HOME%"
if defined SERVER_PORT (
    call :warn "检测到系统环境变量 SERVER_PORT=%SERVER_PORT%，将用 --server.port=8080 强制覆盖"
)

:: ---------- 步骤 2：检查 Maven ----------
call :step "2/5" "检查 Maven"
where mvn >nul 2>&1
if errorlevel 1 goto :no_mvn
for /f "tokens=3" %%v in ('mvn -v 2^>^&1 ^| findstr /b /i /c:"Apache Maven"') do set "MVN_VER=%%v"
call :ok "Maven %MVN_VER% 已就绪"

:: ---------- 步骤 3：检查 MySQL / Redis（仅提示，不阻断）----------
call :step "3/5" "检查 MySQL 3306 与 Redis 6379"
set "WARN_FLAG="
call :port_open 3306
if errorlevel 1 (
    call :warn "MySQL 3306 未监听 —— 后端启动后会连接失败，请先启动 MySQL 服务"
    set "WARN_FLAG=1"
) else (
    call :ok "MySQL 3306 正常"
)
call :port_open 6379
if errorlevel 1 (
    call :warn "Redis 6379 未监听 —— 登录态与缓存不可用，请先启动 Redis"
    set "WARN_FLAG=1"
) else (
    call :ok "Redis 6379 正常"
)

:: ---------- 步骤 4：启动后端 ----------
call :step "4/5" "启动后端 Spring Boot（新窗口）"
call :port_open 8080
if not errorlevel 1 (
    call :wait_http "http://localhost:8080/api/home" 4
    if errorlevel 1 (
        call :err "8080 已被其他程序占用，且 /api/home 无响应"
        echo.
        echo      请先释放端口再运行：netstat -ano ^| findstr :8080
        echo      或结束占用进程：taskkill /PID 进程号 /F
        echo.
        pause
        exit /b 1
    )
    call :warn "8080 上已有本项目的后端在运行，直接复用"
    goto :backend_started
)
if not exist "%ROOT%\backend\pom.xml" goto :no_project
start "后端-8080" /d "%ROOT%\backend" cmd /k "mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080"
call :ok "后端窗口已打开，首次启动需下载依赖，最长等待 3 分钟..."

:backend_started
call :info "等待后端就绪 http://localhost:8080/api/home"
call :wait_http "http://localhost:8080/api/home" 120
if errorlevel 1 (
    call :warn "后端 3 分钟内未就绪，请查看【后端-8080】窗口的报错信息"
    call :warn "常见原因：MySQL 未启动 / 密码不是 123456 / JDK 不是 17"
) else (
    call :ok "后端已就绪，接口返回正常"
)

:: ---------- 步骤 5：启动管理端 ----------
call :step "5/5" "启动管理端 Vite（新窗口）"
if not exist "%ROOT%\admin-web\package.json" goto :no_project
if not exist "%ROOT%\admin-web\node_modules" (
    call :warn "未检测到 admin-web\node_modules，正在安装依赖，请稍候..."
    pushd "%ROOT%\admin-web"
    call npm install
    popd
    call :ok "依赖安装完成"
)
call :port_open 5173
if not errorlevel 1 (
    call :warn "5173 上已有管理端在运行，直接复用"
    goto :admin_started
)
start "管理端-5173" /d "%ROOT%\admin-web" cmd /k "npm run dev"

:admin_started
call :wait_http "http://localhost:5173/" 40
if errorlevel 1 (
    call :warn "管理端未在 60 秒内就绪，请查看【管理端-5173】窗口"
) else (
    call :ok "管理端已就绪"
)

:: ---------- 完成 ----------
call :summary
if defined WARN_FLAG call :warn "存在未就绪的依赖服务，请按上面的提示处理"
echo.
call :ok "按任意键打开管理端登录页，或直接关闭本窗口"
pause >nul
start "" http://localhost:5173
exit /b 0


:: ============================================================
::  以下为子过程
:: ============================================================

:resolve_jdk
set "JDK_FOUND="
:: 1) 项目推荐路径
if exist "D:\jdk-17.0.6\bin\java.exe" (
    set "JDK_FOUND=D:\jdk-17.0.6"
    exit /b 0
)
:: 2) 当前 JAVA_HOME 若已是 17 则直接用
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "VER_OUT="
        for /f "tokens=3" %%v in ('"%JAVA_HOME%\bin\java" -version 2^>^&1 ^| findstr /i "version"') do set "VER_OUT=%%v"
        echo !VER_OUT! | findstr /c:"17." >nul
        if not errorlevel 1 (
            set "JDK_FOUND=%JAVA_HOME%"
            exit /b 0
        )
    )
)
:: 3) 常见安装位置扫描
for /d %%d in ("C:\Program Files\Java\jdk-17*" "C:\Program Files\Eclipse Adoptium\jdk-17*" "C:\Program Files\Microsoft\jdk-17*" "D:\Java\jdk-17*" "D:\Program Files\Java\jdk-17*") do (
    if not defined JDK_FOUND if exist "%%~d\bin\java.exe" set "JDK_FOUND=%%~d"
)
exit /b 0

:port_open
:: %1=端口  返回 errorlevel 0=已监听 1=未监听
netstat -ano | findstr /r /c:"LISTENING" | findstr /c:":%~1 " >nul 2>&1
if errorlevel 1 exit /b 1
exit /b 0

:wait_http
:: %1=url  %2=最多轮询次数（每次 1.5 秒）
powershell -NoProfile -Command "$u='%~1';$n=%~2;for($i=0;$i -lt $n;$i++){try{$q=[System.Net.HttpWebRequest]::Create($u);$q.Proxy=$null;$q.Timeout=2000;$r=$q.GetResponse();$r.Close();exit 0}catch{};Start-Sleep -Milliseconds 1500};exit 1"
if errorlevel 1 exit /b 1
exit /b 0

:banner
powershell -NoProfile -Command "Write-Host ''; Write-Host '  ============================================================' -ForegroundColor DarkCyan; Write-Host '    校园二手交易平台  v1.0.0   一键启动' -ForegroundColor Cyan; Write-Host '    Spring Boot 3.2.5 + Vue 3 + 微信小程序' -ForegroundColor DarkGray; Write-Host '  ============================================================' -ForegroundColor DarkCyan; Write-Host ''"
exit /b 0

:step
powershell -NoProfile -Command "Write-Host ('  [%~1] ' + '%~2') -ForegroundColor White -BackgroundColor DarkBlue"
exit /b 0

:ok
powershell -NoProfile -Command "Write-Host ('      [ OK ] ' + '%~1') -ForegroundColor Green"
exit /b 0

:info
powershell -NoProfile -Command "Write-Host ('      ' + '%~1') -ForegroundColor DarkGray"
exit /b 0

:warn
powershell -NoProfile -Command "Write-Host ('      [WARN] ' + '%~1') -ForegroundColor Yellow"
exit /b 0

:err
powershell -NoProfile -Command "Write-Host ('      [FAIL] ' + '%~1') -ForegroundColor Red"
exit /b 0

:summary
echo.
powershell -NoProfile -Command "Write-Host '  ------------------------------------------------------------' -ForegroundColor DarkCyan; Write-Host '   访问地址' -ForegroundColor Cyan; Write-Host '     管理端    http://localhost:5173      admin / 123456' -ForegroundColor White; Write-Host '     后端接口  http://localhost:8080/api/home' -ForegroundColor White; Write-Host '     接口文档  http://localhost:8080/doc.html' -ForegroundColor White; Write-Host '     小程序    用微信开发者工具导入 miniprogram 目录' -ForegroundColor White; Write-Host '              详情-本地设置-勾选 不校验合法域名' -ForegroundColor DarkGray; Write-Host '  ------------------------------------------------------------' -ForegroundColor DarkCyan; Write-Host '   提示：管理端必须用 localhost，不能用 127.0.0.1' -ForegroundColor DarkGray; Write-Host '         Vite 只监听 IPv6 回环 ::1' -ForegroundColor DarkGray; Write-Host '   停止：双击 stop.bat' -ForegroundColor DarkGray; Write-Host '  ------------------------------------------------------------' -ForegroundColor DarkCyan"
exit /b 0

:no_jdk
call :err "未找到 JDK 17"
echo.
echo      请安装 JDK 17 后重试，或手动指定路径：
echo          set JAVA_HOME=D:\jdk-17.0.6
echo      注意：不能用 JDK 21 / 26，Lombok 会编译失败
echo      下载：https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html
echo.
pause
exit /b 1

:no_mvn
call :err "未找到 Maven 命令 mvn"
echo.
echo      请安装 Maven 3.6+ 并加入 PATH，或检查 MAVEN_HOME
echo      验证：mvn -v
echo.
pause
exit /b 1

:no_project
call :err "当前目录不是项目根目录，缺少 backend\pom.xml 或 admin-web\package.json"
echo.
echo      当前目录：%ROOT%
echo      请把 start.bat 放在 campus-market 根目录后运行
echo.
pause
exit /b 1
