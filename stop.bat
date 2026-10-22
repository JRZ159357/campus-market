@echo off
setlocal EnableExtensions EnableDelayedExpansion
title 校园二手交易平台 · 一键停止

:: ============================================================
::  校园二手交易平台 v1.0.0  一键停止脚本（Windows）
::  用法：双击运行，或在项目根目录执行  stop.bat
::  作用：结束后端 8080 与管理端 5173 上监听的进程，并清理残留窗口
::  说明：只停本项目占用的端口，不会误杀其他 Java / Node 程序；
::        不停止 MySQL 与 Redis 系统服务（如需停止请到服务管理器操作）
:: ============================================================

cd /d "%~dp0"
set "TOTAL=0"

call :banner
call :step "停止" "停止后端与管理端"

call :killport 8080 "后端 Spring Boot"
call :killport 5173 "管理端 Vite"

:: 兜底：关闭由 start.bat 打开、但进程已退出的残留命令行窗口
call :killwin "后端-8080"
call :killwin "管理端-5173"

echo.
if "!TOTAL!"=="0" (
    call :warn "未发现正在运行的后端或管理端，端口 8080 / 5173 均空闲"
) else (
    call :ok "已停止 !TOTAL! 个监听进程"
)

call :port_open 8080
if errorlevel 1 (
    call :ok "端口 8080 已释放"
) else (
    call :err "端口 8080 仍被占用，请手动执行：netstat -ano ^| findstr :8080"
)

call :port_open 5173
if errorlevel 1 (
    call :ok "端口 5173 已释放"
) else (
    call :err "端口 5173 仍被占用，请手动执行：netstat -ano ^| findstr :5173"
)

echo.
powershell -NoProfile -Command "Write-Host '  ------------------------------------------------------------' -ForegroundColor DarkCyan; Write-Host '   提示：MySQL 3306 与 Redis 6379 为系统服务，未随脚本停止' -ForegroundColor DarkGray; Write-Host '         如需停止：net stop mysql80  /  taskkill /IM redis-server.exe' -ForegroundColor DarkGray; Write-Host '  ------------------------------------------------------------' -ForegroundColor DarkCyan"
echo.
call :ok "按任意键关闭窗口"
pause >nul
exit /b 0


:: ============================================================
::  以下为子过程
:: ============================================================

:killport
:: %1=端口  %2=名称   结束该端口上的 LISTENING 进程（含子进程树）
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:"LISTENING" ^| findstr /c:":%~1 "') do (
    taskkill /PID %%p /T /F >nul 2>&1
    if not errorlevel 1 (
        echo       [KILL] %~2  PID %%p  端口 %~1
        set /a TOTAL+=1
    )
)
exit /b 0

:killwin
:: %1=窗口标题前缀  关闭残留的命令行窗口
taskkill /FI "WINDOWTITLE eq %~1*" /T /F >nul 2>&1
exit /b 0

:port_open
:: %1=端口  返回 errorlevel 0=已监听 1=未监听
netstat -ano | findstr /c:"LISTENING" | findstr /c:":%~1 " >nul 2>&1
if errorlevel 1 exit /b 1
exit /b 0

:banner
powershell -NoProfile -Command "Write-Host ''; Write-Host '  ============================================================' -ForegroundColor DarkCyan; Write-Host '    校园二手交易平台  v1.0.0   一键停止' -ForegroundColor Cyan; Write-Host '  ============================================================' -ForegroundColor DarkCyan; Write-Host ''"
exit /b 0

:step
powershell -NoProfile -Command "Write-Host ('  [%~1] ' + '%~2') -ForegroundColor White -BackgroundColor DarkBlue"
exit /b 0

:ok
powershell -NoProfile -Command "Write-Host ('      [ OK ] ' + '%~1') -ForegroundColor Green"
exit /b 0

:warn
powershell -NoProfile -Command "Write-Host ('      [WARN] ' + '%~1') -ForegroundColor Yellow"
exit /b 0

:err
powershell -NoProfile -Command "Write-Host ('      [FAIL] ' + '%~1') -ForegroundColor Red"
exit /b 0
