@echo off
chcp 65001 >nul
title 农事平台 · 管理端 (5174)
cd /d "%~dp0frontend-admin"
if not exist node_modules (
  echo 首次运行，正在安装依赖...
  call npm install
)
start "" http://localhost:5174
call npm run dev
