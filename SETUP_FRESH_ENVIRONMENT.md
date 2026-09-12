# MarketPulse — Fresh Environment Setup Guide

This guide details everything required to clone, build, and run **MarketPulse** on a brand-new computer or environment.

---

## 1. Summary of Requirements

| Requirement | Needed? | Details |
|---|---|---|
| **Java JDK 24** (or JDK 21+) | **MANDATORY** | Used to compile and run the exchange matching engine & REST server. |
| **Maven** | **NO** (Pre-bundled) | A standalone Maven wrapper (`mvnw.cmd` and `maven/`) is included in this repository. |
| **Database** | **NO** (Pre-bundled) | In-memory H2 database runs automatically inside Java. No external DB setup required. |
| **Node.js / npm** | **NO** | Web terminal frontend uses pure Vanilla HTML5, CSS3, and ES6 JavaScript. |
| **Python** | **OPTIONAL** | Only needed if you want to regenerate the academic PDF reports or screenshot tool in `/scripts`. |

---

## 2. Step-by-Step Setup

### Step 1: Install Java (JDK 24)
1. Download **JDK 24** (or **JDK 21+**) for your operating system:
   * **Eclipse Adoptium (Recommended):** [https://adoptium.net/temurin/releases/](https://adoptium.net/temurin/releases/)
   * **Oracle OpenJDK:** [https://jdk.java.net/24/](https://jdk.java.net/24/)
2. Run the installer. Ensure you check **"Set JAVA_HOME variable"** and **"Add to PATH"** during installation.
3. Open a terminal and verify:
   ```bash
   java -version
   ```

### Step 2: Verify Your Environment
Run the included pre-flight checker script by double-clicking:
```cmd
check_prerequisites.bat
```
This script checks your Java installation, validates the bundled Maven wrapper, and ensures Port 8080 is available.

---

## 3. Running MarketPulse

### Option A: 1-Click Launch (Windows)
Double-click:
```cmd
run.bat
```
This script will:
1. Compile the Java source code with Maven.
2. Launch the `ExchangeServer` on port `8080`.
3. Automatically open your default web browser to `http://localhost:8080`.

### Option B: Command Line Launch
Run using the bundled Maven wrapper:

* **Windows:**
  ```cmd
  .\mvnw.cmd compile exec:java
  ```
* **Linux / macOS:**
  ```bash
  chmod +x ./mvnw
  ./mvnw compile exec:java
  ```

---

## 4. Running Unit & Concurrency Tests

To run all 28 automated test suites (covering order matching, concurrency invariants, and persistence):
```cmd
.\mvnw.cmd test
```

---

## 5. (Optional) Python Scripts Setup

If you want to run the automated screenshot capture or PDF report generators inside [`scripts/`](file:///e:/flipped%20course%20project/scripts):

```bash
# 1. Install Python packages
pip install -r requirements.txt

# 2. Install Chromium binaries for Playwright
playwright install chromium

# 3. Generate report / screenshots
python scripts/generate_pdf_report.py
```
