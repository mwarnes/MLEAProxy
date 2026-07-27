# MLEAProxy Distribution Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** Create a distributable zip package containing MLEAProxy JAR, user documentation, examples, and runtime scripts for MarkLogic engineers to deploy on clean macOS/Linux machines.

**Architecture:** Single shell script (create-distribution.sh) that validates portability, assembles distribution directory structure, generates new user-facing scripts (interactive launcher, keytab helper), and creates zip archive. All components use relative paths and portable constructs.

**Tech Stack:** Bash shell scripts, standard Unix utilities (zip, grep, find), ktutil (Kerberos)

## Global Constraints

- All scripts use #!/bin/bash shebang
- Use relative paths only: ./, ../, ${SCRIPT_DIR}
- No hardcoded paths: /Users/martin/, /home/, /opt/
- No machine-specific hostnames (except localhost, 127.0.0.1)
- Environment variables allowed: ${HOME}, ${JAVA_HOME}, ${USER}
- Scripts must work on macOS and Linux
- JAR location: target/mlesproxy-2.0.3.jar or release/mlesproxy-2.0.3.jar
- Distribution version: 2.0.3
- Target zip size: 60-70 MB
- All .sh files must be executable (chmod +x)

---

## Task Summary

1. Create README.txt quick start guide
2. Create interactive launcher (scripts/start.sh)
3. Create keytab generation helper (scripts/create-keytab.sh)
4. Create keytab directory README
5. Create portability validation function
6. Create main distribution script (create-distribution.sh)
7. End-to-end testing

Each task produces working, testable components. All scripts are portable and use relative paths only.

**Total estimated time:** 3-4 hours

Ready for execution via subagent-driven-development or executing-plans skill.
