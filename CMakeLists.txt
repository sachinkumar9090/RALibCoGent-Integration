cmake_minimum_required(VERSION 3.15)
project(RALib_CoGent_Pipeline NONE)

# ===== STEP 0: Helper function =====
function(run_cmd_safe cmd)
    execute_process(
        COMMAND ${cmd}
        RESULT_VARIABLE result
    )
    if(NOT result EQUAL 0)
        message(FATAL_ERROR "Command failed: ${cmd}")
    endif()
endfunction()

# CheckBrew.cmake
# Checks if Homebrew is installed on macOS and Linux.

# Detect OS
if(APPLE OR UNIX)
    message(STATUS "Checking for Homebrew...")

    find_program(BREW_EXECUTABLE brew)

    if(NOT BREW_EXECUTABLE)
        if(APPLE)
            message(FATAL_ERROR "Homebrew is not installed on your macOS system.\nPlease install using:\n/bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"")
        elseif(UNIX)
            message(FATAL_ERROR "Homebrew is not installed on your Linux system.\nPlease install using:\n/bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"")
        endif()
    else()
        execute_process(
            COMMAND ${BREW_EXECUTABLE} --version
            OUTPUT_VARIABLE BREW_VERSION_OUTPUT
            OUTPUT_STRIP_TRAILING_WHITESPACE
        )
        message(STATUS "Homebrew found: ${BREW_EXECUTABLE}")
        message(STATUS "Homebrew version: ${BREW_VERSION_OUTPUT}")
    endif()
else()
    message(WARNING "Homebrew check is only supported on macOS and Linux. Skipping Homebrew check on this OS.")
endif()


# ===== STEP 1: Check Git and install =====
find_program(GIT_EXECUTABLE git)
if(NOT GIT_EXECUTABLE)
    message(WARNING "Git not found. Attempting to install...")
    if(APPLE)
        find_program(BREW_EXECUTABLE brew)
        if(NOT BREW_EXECUTABLE)
            message(STATUS "Installing Homebrew...")
            execute_process(COMMAND /bin/bash -c "curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh | /bin/bash"
                RESULT_VARIABLE BREW_INSTALL_RESULT)
            if(NOT BREW_INSTALL_RESULT EQUAL 0)
                message(FATAL_ERROR "Homebrew installation failed.")
            endif()
            set(ENV{PATH} "/usr/local/bin:/opt/homebrew/bin:$ENV{PATH}")
        endif()
        execute_process(COMMAND brew install git RESULT_VARIABLE BREW_GIT_RESULT)
        if(NOT BREW_GIT_RESULT EQUAL 0)
            message(FATAL_ERROR "Git installation failed on macOS.")
        endif()
    elseif(UNIX)
        execute_process(COMMAND sudo apt update)
        execute_process(COMMAND sudo apt install git -y RESULT_VARIABLE APT_GIT_RESULT)
        if(NOT APT_GIT_RESULT EQUAL 0)
            message(FATAL_ERROR "Git installation failed on Linux.")
        endif()
    elseif(WIN32)
        message(FATAL_ERROR "Git not found. Please install manually from https://git-scm.com/download/win")
    endif()
    find_program(GIT_EXECUTABLE git)
endif()
message(STATUS "Git found: ${GIT_EXECUTABLE}")

# ===== STEP 2: Clone RALib =====
set(RALIB_DIR "${CMAKE_SOURCE_DIR}/ralib")
if(NOT EXISTS "${RALIB_DIR}")
    message(STATUS "Cloning RALib...")
    execute_process(COMMAND ${GIT_EXECUTABLE} clone https://github.com/LearnLib/ralib.git "${RALIB_DIR}"
        RESULT_VARIABLE GIT_CLONE_RESULT)
    if(NOT GIT_CLONE_RESULT EQUAL 0)
        message(FATAL_ERROR "Failed to clone RALib.")
    endif()
else()
    message(STATUS "RALib already cloned.")
endif()

# ===== STEP 3: Check Java =====
find_program(JAVA_EXECUTABLE java)
if(NOT JAVA_EXECUTABLE)
    message(FATAL_ERROR "Java not found. Install Java 17+ and add to PATH.")
endif()
execute_process(
    COMMAND ${JAVA_EXECUTABLE} -version
    OUTPUT_VARIABLE JAVA_VERSION_OUTPUT
    ERROR_VARIABLE JAVA_VERSION_ERROR
    OUTPUT_STRIP_TRAILING_WHITESPACE
    ERROR_STRIP_TRAILING_WHITESPACE
)
set(JAVA_VERSION_STRING "${JAVA_VERSION_ERROR}")
string(REGEX MATCH "\"[0-9]+(\\.[0-9]+)*\"" JAVA_VERSION_MATCH ${JAVA_VERSION_STRING})
if(JAVA_VERSION_MATCH)
    string(REPLACE "\"" "" JAVA_VERSION_NUMBER ${JAVA_VERSION_MATCH})
    string(REGEX REPLACE "^([0-9]+).*" "\\1" JAVA_MAJOR_VERSION "${JAVA_VERSION_NUMBER}")
    message(STATUS "Java detected: ${JAVA_VERSION_NUMBER} (using major ${JAVA_MAJOR_VERSION})")
else()
    message(WARNING "Could not parse Java version.")
endif()

# ===== STEP 4: Check Maven =====
find_program(MAVEN_EXECUTABLE mvn)
if(NOT MAVEN_EXECUTABLE)
    message(WARNING "Maven not found. Attempting to install...")
    if(APPLE)
        find_program(BREW_EXECUTABLE brew)
        if(NOT BREW_EXECUTABLE)
            execute_process(COMMAND /bin/bash -c "curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh | /bin/bash")
            set(ENV{PATH} "/usr/local/bin:/opt/homebrew/bin:$ENV{PATH}")
        endif()
        execute_process(COMMAND brew install maven RESULT_VARIABLE BREW_MAVEN_RESULT)
        if(NOT BREW_MAVEN_RESULT EQUAL 0)
            message(FATAL_ERROR "Maven installation failed on macOS.")
        endif()
    elseif(UNIX)
        execute_process(COMMAND sudo apt update)
        execute_process(COMMAND sudo apt install maven -y RESULT_VARIABLE APT_MAVEN_RESULT)
        if(NOT APT_MAVEN_RESULT EQUAL 0)
            message(FATAL_ERROR "Maven installation failed on Linux.")
        endif()
    elseif(WIN32)
        message(FATAL_ERROR "Maven not found. Install from https://maven.apache.org/download.cgi and add to PATH.")
    endif()
    find_program(MAVEN_EXECUTABLE mvn)
endif()
message(STATUS "Maven found: ${MAVEN_EXECUTABLE}")

# ===== STEP 5: Modify pom.xml =====
set(POM_FILE "${RALIB_DIR}/pom.xml")
if(EXISTS "${POM_FILE}" AND JAVA_MAJOR_VERSION)
    file(READ "${POM_FILE}" POM_CONTENTS)
    string(REGEX REPLACE "<release>[0-9]+</release>"
                         "<release>${JAVA_MAJOR_VERSION}</release>"
                         POM_CONTENTS_MODIFIED
                         "${POM_CONTENTS}")
    file(WRITE "${POM_FILE}" "${POM_CONTENTS_MODIFIED}")
    message(STATUS "Updated Java version in pom.xml to ${JAVA_MAJOR_VERSION}.")
endif()

# ===== STEP 6: Build RALib =====
file(GLOB RALIB_JARS "${RALIB_DIR}/target/ralib*-with-dependencies.jar")
list(LENGTH RALIB_JARS RALIB_JAR_COUNT)

if(RALIB_JAR_COUNT GREATER 0)
    message(STATUS "RALib already built.")
else()
    message(STATUS "Building RALib...")
    execute_process(
        COMMAND ${MAVEN_EXECUTABLE} package assembly:single
        WORKING_DIRECTORY ${RALIB_DIR}
        RESULT_VARIABLE MAVEN_BUILD_RESULT
    )
    if(NOT MAVEN_BUILD_RESULT EQUAL 0)
        message(FATAL_ERROR "RALib build failed.")
    endif()
    message(STATUS "RALib built successfully.")
endif()

# ===== STEP 7: Clone CoGent =====
set(COGENT_DIR "${CMAKE_SOURCE_DIR}/cogent")
if(NOT EXISTS "${COGENT_DIR}")
    message(STATUS "Cloning CoGent...")
    execute_process(COMMAND ${GIT_EXECUTABLE} clone https://github.com/sandipghosal/CoGent.git "${COGENT_DIR}"
        RESULT_VARIABLE GIT_COGENT_RESULT)
    if(NOT GIT_COGENT_RESULT EQUAL 0)
        message(FATAL_ERROR "Failed to clone CoGent.")
    endif()
else()
    message(STATUS "CoGent already cloned.")
endif()

# ===== STEP 8: Check Python 3 =====
# Check if python3 is installed
find_program(PYTHON_EXECUTABLE python3)

if(NOT PYTHON_EXECUTABLE)
    message(STATUS "Python 3 not found. Installing using brew...")

    execute_process(
        COMMAND brew install python
        RESULT_VARIABLE BREW_INSTALL_RESULT
    )

    if(NOT BREW_INSTALL_RESULT EQUAL 0)
        message(FATAL_ERROR "Failed to install Python 3 using brew. Please check your brew setup.")
    endif()

    # Try to find python3 again
    find_program(PYTHON_EXECUTABLE python3)

    if(NOT PYTHON_EXECUTABLE)
        message(FATAL_ERROR "Python 3 installed, but not found in PATH. Please check your PATH.")
    endif()
endif()

message(STATUS "Python found: ${PYTHON_EXECUTABLE}")


# ===== STEP 9: Check/Install SymPy =====
execute_process(
    COMMAND ${PYTHON_EXECUTABLE} -c "import sympy; print(sympy.__version__)"
    OUTPUT_VARIABLE SYMPY_OUTPUT
    ERROR_VARIABLE SYMPY_ERROR
    RESULT_VARIABLE SYMPY_RESULT
    OUTPUT_STRIP_TRAILING_WHITESPACE
    ERROR_STRIP_TRAILING_WHITESPACE
)

if(NOT SYMPY_RESULT EQUAL 0)
    message(WARNING "SymPy not found. Installing using pipx...")

    # Install pipx using brew
    execute_process(
        COMMAND brew install pipx
        RESULT_VARIABLE BREW_PIPX_RESULT
    )
    if(NOT BREW_PIPX_RESULT EQUAL 0)
        message(FATAL_ERROR "Failed to install pipx using brew. Please install manually using 'brew install pipx'.")
    endif()

    # Ensure pipx is initialized
    execute_process(
        COMMAND pipx ensurepath
        RESULT_VARIABLE PIPX_ENSUREPATH_RESULT
    )
    if(NOT PIPX_ENSUREPATH_RESULT EQUAL 0)
        message(WARNING "pipx ensurepath failed. Please ensure pipx is correctly initialized.")
    endif()

    # Install sympy using pipx
    execute_process(
        COMMAND pipx install sympy
        RESULT_VARIABLE PIPX_INSTALL_SYMPY_RESULT
    )
    if(NOT PIPX_INSTALL_SYMPY_RESULT EQUAL 0)
        message(FATAL_ERROR "Failed to install SymPy using pipx. Please install manually:\nFor macOS: brew install pipx && pipx install sympy\nFor Linux: pip install sympy --break-system-packages")
    endif()

    message(STATUS "SymPy installed using pipx.")
else()
    message(STATUS "SymPy found: ${SYMPY_OUTPUT}")
endif()


# ===== STEP 10: Compile RALibRunner.java and Package Fat Jar =====
set(RUNNER_JAVA "${CMAKE_SOURCE_DIR}/RALibRunner.java")
set(RUNNER_CLASS "RALibRunner.class")
set(RALIB_JAR "ralib/target/ralib-0.1-SNAPSHOT-jar-with-dependencies.jar")
set(FAT_JAR "${CMAKE_SOURCE_DIR}/ralib-fat-runner.jar")

if(NOT EXISTS "${RALIB_JAR}")
    message(FATAL_ERROR "RALib jar not found: ${RALIB_JAR}. Build RALib first.")
endif()

message(STATUS "Compiling ${RUNNER_JAVA}...")
execute_process(
    COMMAND javac -cp "${RALIB_JAR}" "${RUNNER_JAVA}"
    RESULT_VARIABLE RUNNER_COMPILE_RESULT
)
if(NOT RUNNER_COMPILE_RESULT EQUAL 0)
    message(FATAL_ERROR "Failed to compile RALibRunner.java")
endif()

message(STATUS "Packaging fat jar: ${FAT_JAR}...")
execute_process(COMMAND ${CMAKE_COMMAND} -E remove -f "${FAT_JAR}")
execute_process(COMMAND ${CMAKE_COMMAND} -E make_directory tmp_jar_build/lib)
execute_process(COMMAND ${CMAKE_COMMAND} -E copy "${RUNNER_CLASS}" tmp_jar_build/)
execute_process(COMMAND ${CMAKE_COMMAND} -E chdir tmp_jar_build/lib jar xf "../../${RALIB_JAR}")
file(WRITE "tmp_jar_build/manifest.txt" "Main-Class: RALibRunner\n")
execute_process(
    COMMAND jar cfm "${FAT_JAR}" tmp_jar_build/manifest.txt -C tmp_jar_build RALibRunner.class -C tmp_jar_build/lib .
    RESULT_VARIABLE JAR_RESULT
)
if(NOT JAR_RESULT EQUAL 0)
    message(FATAL_ERROR "Failed to create fat jar.")
endif()
execute_process(COMMAND ${CMAKE_COMMAND} -E remove_directory tmp_jar_build)
execute_process(COMMAND ${CMAKE_COMMAND} -E remove -f "${RUNNER_CLASS}")

message(STATUS "")
message(STATUS "Fat jar created: ${FAT_JAR}")
message(STATUS "Build complete.")
message(STATUS "You can now run your contracts using:")
message(STATUS "---------------------------------------------")
message(STATUS "---------------------------------------------")
message(STATUS "java -ea -jar \"${FAT_JAR}\" <java_or_class_file> <config_path> <target_method>")
message(STATUS "---------------------------------------------")
message(STATUS "---------------------------------------------")
message(STATUS "Use This Command if You have existing_ Model.xml  ")
message(STATUS "java -ea -jar \"${FAT_JAR}\" <existing_model.xml> <target_method>")
message(STATUS "ALL TASKS COMPLETE NOW")
