@echo off
setlocal
for %%F in (%~nx0) do set "_script_name=%%~nF"
call "%~dp0_build_common.bat" start %_script_name%
call "%~dp0env.bat"

rem 简化生成 placeholder.out，内容为 build number
if defined BUILD_NUMBER (
  echo %BUILD_NUMBER% > placeholder.out
) else (
  echo unknown > placeholder.out
)

call "%~dp0_build_common.bat" end %_script_name%
endlocal
