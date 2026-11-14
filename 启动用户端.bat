@echo off
chcp 65001 >nul
title 农事平台 · 用户端 (5173)
cd /d "%~dp0frontend"
if not exist node_modules (
  echo 首次运行，正在安装依赖...
  call npm install
)
start "" http://localhost:5173
call npm run dev
