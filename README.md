# RALib-CoGent Pipeline

This project automates the setup and integration of [RALib](https://github.com/LearnLib/ralib) and [CoGent](https://github.com/sandipghosal/CoGent) using a CMake pipeline.

## Features

- Installs required tools (cmake, Git, Java 17+, Maven, Python 3, SymPy)
- Clones RALib and CoGent repositories
- Builds RALib with Maven
- Compiles `RALibRunner.java`
- Packages a runnable fat JAR

---

## 🖥 Supported Platforms

✅ macOS  
✅ Linux (Debian/Ubuntu)  
⚠️ Windows (manual setup required)

---

## ⚙️ Installation Steps :

## For All Operating System 

Step 1: Check cmake in your System using Commad on Your terminal : cmake --version 
        if found : cmake version 4.x.x.
        or     
        Not found : 
        For Linux : use this command : 1. sudo apt update
                                       2. sudo apt install cmake

        For Macos : Install Homebrew First 
        Just Pate this on your terminal : /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        Then use this command : brew install cmake

        For Windows : Install Manually : https://cmake.org/download/

Step 2: Check java in your System using : java --version
        if found : java 24.x.x.
        else : Downlaod latest java jdk in your system 

Step 3: Now To run this RALibCoGent-Integration file Use this command on Terminal 
        --> cmake . or cmake CMakeLists.txt

For Windows User Only 

Step 1: Install Python 3

1. Go to the official Python website:
👉 https://www.python.org/downloads/

2. Click “Download Python 3.x.x” (latest version).

3 .Run the installer.

 IMPORTANT: Check the box that says:
"Add Python to PATH" at the bottom of the installer window.

Click “Install Now” and wait for installation to complete.

Step 2: Verify Python and pip installation
Open CommandPrompt(CMD) :
Use this Command To Verify Python3 and pip : python --version  and pip --version

Step 3: Install SymPy using pip
Use This Command to install sympy : pip install sympy 


🚀 Run the Tool
After build, run your learning target like this: java -ea -jar ralib-fat-runner.jar <java or class file> <ConfigPath> <TargetMethod>
