@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@REM Begin all REM://- sym link respecting
@REM
@REM Temporary disable delayed expansion to avoid issues with ! in paths
@setlocal & set "MVNW_VERBOSE=%MVNW_VERBOSE%"

@IF NOT DEFINED MVNW_VERBOSE SET MVNW_VERBOSE=false

@REM Determine the project base directory
@SET "MVNW_DIR=%~dp0"
@SET "MVNW_DIR=%MVNW_DIR:~0,-1%"

@REM Locate the maven-wrapper.properties
@SET "WRAPPER_PROPERTIES=%MVNW_DIR%\.mvn\wrapper\maven-wrapper.properties"

@REM Check if the wrapper properties file exists
@IF NOT EXIST "%WRAPPER_PROPERTIES%" (
    @ECHO Error: Could not find .mvn\wrapper\maven-wrapper.properties. >&2
    @ECHO Please check if the file exists. >&2
    @EXIT /B 1
)

@REM Parse distributionUrl from maven-wrapper.properties
@SET WRAPPER_URL=
@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%WRAPPER_PROPERTIES%") DO (
    @IF "%%A"=="distributionUrl" SET "WRAPPER_URL=%%B"
)

@IF "%WRAPPER_URL%"=="" (
    @ECHO Error: Could not find distributionUrl in %WRAPPER_PROPERTIES% >&2
    @EXIT /B 1
)

@REM Determine Maven home directory
@SET "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists"

@REM Extract the Maven version from the URL
@SET "WRAPPER_URL_NORMALIZED=%WRAPPER_URL:/=\%"
@FOR %%F IN ("%WRAPPER_URL_NORMALIZED%") DO @SET "DIST_NAME=%%~nF"

@REM Set Maven home to the specific distribution
@SET "MAVEN_HOME=%MAVEN_HOME%\%DIST_NAME%"

@REM Check if Maven is already downloaded
@IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" GOTO :runMaven

@REM Download Maven
@ECHO Downloading from: %WRAPPER_URL%
@ECHO Downloading to: %MAVEN_HOME%

@IF NOT EXIST "%MAVEN_HOME%" @MKDIR "%MAVEN_HOME%"

@REM Use PowerShell to download
@SET "DOWNLOAD_FILE=%MAVEN_HOME%\%DIST_NAME%.zip"

@powershell -Command ^
    "$ProgressPreference = 'SilentlyContinue'; " ^
    "try { " ^
    "  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; " ^
    "  Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%DOWNLOAD_FILE%' -UseBasicParsing; " ^
    "} catch { " ^
    "  Write-Error \"Error downloading Maven: $_\"; " ^
    "  exit 1; " ^
    "}"

@IF %ERRORLEVEL% NEQ 0 (
    @ECHO Error: Failed to download Maven distribution. >&2
    @EXIT /B 1
)

@REM Extract the archive
@ECHO Extracting Maven...
@powershell -Command ^
    "try { " ^
    "  Expand-Archive -Path '%DOWNLOAD_FILE%' -DestinationPath '%MAVEN_HOME%' -Force; " ^
    "} catch { " ^
    "  Write-Error \"Error extracting Maven: $_\"; " ^
    "  exit 1; " ^
    "}"

@IF %ERRORLEVEL% NEQ 0 (
    @ECHO Error: Failed to extract Maven distribution. >&2
    @EXIT /B 1
)

@REM Move contents from inner directory
@FOR /D %%D IN ("%MAVEN_HOME%\apache-maven-*") DO (
    @XCOPY "%%D\*" "%MAVEN_HOME%\" /S /E /Y /Q >NUL 2>&1
    @RMDIR "%%D" /S /Q >NUL 2>&1
)

@REM Clean up the zip file
@DEL "%DOWNLOAD_FILE%" >NUL 2>&1

:runMaven
@REM Set JAVA_HOME if not set
@IF NOT DEFINED JAVA_HOME (
    @FOR %%I IN (java.exe) DO @SET "JAVACMD=%%~$PATH:I"
) ELSE (
    @SET "JAVACMD=%JAVA_HOME%\bin\java.exe"
)

@IF NOT EXIST "%JAVACMD%" (
    @ECHO Error: JAVA_HOME is not defined correctly or java is not in the PATH. >&2
    @ECHO Please set JAVA_HOME to point to your JDK installation. >&2
    @EXIT /B 1
)

@REM Set Maven options
@SET "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"

@IF NOT EXIST "%MAVEN_CMD%" (
    @ECHO Error: Maven installation not found at %MAVEN_HOME% >&2
    @ECHO Please delete %MAVEN_HOME% and try again. >&2
    @EXIT /B 1
)

@REM Run Maven
@"%MAVEN_CMD%" %*
