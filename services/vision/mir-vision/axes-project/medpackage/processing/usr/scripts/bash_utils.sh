#!/bin/bash
# INRIA LEAR team, 2015
# Xavier Martin xavier.martin@inria.fr

TXT_BLACK="[30m"
TXT_RED="[31m"
TXT_GREEN="[32m"
TXT_YELLOW="[33m"
TXT_BLUE="[34m"
TXT_MAGENTA="[35m"
TXT_CYAN="[36m"
TXT_WHITE="[37m"

TXT_BG_BLACK="[40m"
TXT_BG_RED="[41m"
TXT_BG_GREEN="[42m"
TXT_BG_YELLOW="[43m"
TXT_BG_BLUE="[44m"
TXT_BG_MAGENTA="[45m"
TXT_BG_CYAN="[46m"
TXT_BG_WHITE="[47m"

TXT_BOLD="[1m"
TXT_UNDERSCORE="[4m"
TXT_BLINK="[5m"


TXT_RESET="[0m"

#[    ]
#[ OK ]
#[WARN]
#[ ERR]

TITLE="YES"
function log_TITLE {
	if [ "${TITLE}" == "YES" ]; then
		echo "${TXT_BOLD}${TXT_WHITE}${TXT_BG_CYAN}$1${TXT_RESET}"
	fi
}

function log_INFO {
	echo "       $1"
}

function log_IMPORTANT {
	echo "${TXT_BOLD}------ $1${TXT_RESET}"
}

function log_OK {

	echo "[ ${TXT_GREEN}OK${TXT_RESET} ]${TXT_RESET} $1"
}

function log_WARN {
	echo "[${TXT_RED}WARN${TXT_RESET}]${TXT_RESET} $1"
}

function log_ERR {
	echo "[${TXT_BOLD}${TXT_RED}FAIL${TXT_RESET}]${TXT_RESET} $1"
	#echo "${TXT_BOLD}${TXT_WHITE}${TXT_BG_RED}$1${TXT_RESET}"
}


#DEBUG=YES
function log_DEBUG {
	if [ ! -z ${DEBUG+x} ]; then
		echo "${TXT_BOLD}${TXT_BG_BLUE}${TXT_GREEN}[DBG ]${TXT_RESET} $1"
	fi
}

TODO=YES
function log_TODO {
	if [ ! -z ${TODO+x} ]; then
		echo "${TXT_BOLD}${TXT_BG_RED}${TXT_GREEN}[TODO]${TXT_RESET} $1"
	fi
}

function test_INFO {
	echo "${TXT_BOLD}${TXT_WHITE}${TXT_BG_CYAN}$1${TXT_RESET}"
}
function test_OK {
	echo "${TXT_BOLD}${TXT_WHITE}${TXT_BG_GREEN}$1${TXT_RESET}"
}
function test_WARN {
	echo "${TXT_BOLD}${TXT_WHITE}${TXT_BG_YELLOW}$1${TXT_RESET}"
}
function test_ERR {
	echo "${TXT_BOLD}${TXT_WHITE}${TXT_BG_RED}$1${TXT_RESET}"
}


