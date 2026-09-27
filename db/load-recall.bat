@echo off
REM ==========================================================================
REM  Recall data load  -  for teammates.
REM  Prerequisite: run src\main\resources\schema.sql first.
REM  (Korean notes are in README.md - batch files stay ASCII on purpose.)
REM ==========================================================================
setlocal enabledelayedexpansion

set "DB_NAME=recallcheck"
set "DB_USER=root"
set "DUMP=%~dp0recall-dump.sql"
set "MYSQLEXE="

if not exist "%DUMP%" (
  echo.
  echo [ERROR] Dump file not found: %DUMP%
  echo         Run "git pull" first.
  echo.
  pause
  exit /b 1
)

echo.
echo === Looking for mysql.exe ===

for /f "delims=" %%P in ('where mysql 2^>nul') do if not defined MYSQLEXE set "MYSQLEXE=%%P"

if not defined MYSQLEXE (
  for %%D in (
    "C:\Program Files\MySQL\MySQL Server 8.0\bin"
    "C:\Program Files\MySQL\MySQL Server 8.4\bin"
    "C:\Program Files\MySQL\MySQL Shell 8.0\bin"
    "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin"
    "C:\xampp\mysql\bin"
    "C:\Bitnami\mysql\bin"
  ) do if not defined MYSQLEXE if exist "%%~D\mysql.exe" set "MYSQLEXE=%%~D\mysql.exe"
)

if not defined MYSQLEXE (
  echo.
  echo [ERROR] mysql.exe not found. See README.md for the IntelliJ route.
  echo.
  pause
  exit /b 1
)

echo Found: !MYSQLEXE!
echo.
echo This REPLACES the recall and recall_file tables in %DB_NAME%.
echo Existing recall rows will be gone. Press any key to continue, or close this window.
pause > nul
echo.
echo Enter the MySQL password when asked.
echo.

"!MYSQLEXE!" -u %DB_USER% -p --default-character-set=utf8mb4 %DB_NAME% < "%DUMP%"

if errorlevel 1 (
  echo.
  echo [ERROR] Load failed.
  echo.
  echo If you saw "Cannot delete or update a parent row", old test rows in
  echo match_result still reference recall. Run this in MySQL, then retry:
  echo.
  echo     DELETE FROM match_result;
  echo     DELETE FROM extraction;
  echo     DELETE FROM verification;
  echo.
  pause
  exit /b 1
)

echo.
echo Done. Verify with the query in README.md.
echo Expected: 778 rows, and 0 in both empty-string columns.
echo.
pause
endlocal
