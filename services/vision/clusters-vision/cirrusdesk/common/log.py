""" Application-level logging for Python in the style of Google's glog.  

This library provides logging APIs based on Google's glog library (for C++). 
You can log a message by simply calling:

LOG(<a particular severity level>, message), e.g.

   from common.log import *
   LOG(INFO, "Found %d cookies" % num)
   LOG(FATAL, "Can't open file %s" % filename)
   
Also provides checks that simplify many common logging tasks. You can log
messages based on conditionals, abort the program when expected conditions are
not met with a helpful message.  Examples:

CHECK(a)
CHECK_EQ(a, b) 
CHECK_NE(a, b)
CHECK_LE(a, b)
CHECK_GE(a, b)
CHECK_LT(a, b)
CHECK_GT(a, b)
CHECK_NOTNONE(a)
"""

import datetime
import os
import sys
import time
import traceback

GLOBAL_LOG_LEVEL = 1  # global logging level constant

DEBUG = 2  # INFO level -> print message to stderr and stack trace
INFO = 1  # INFO level -> print message to stderr
FATAL = -1  # FATAL level -> print message and stack trace to stderr and exit


def set_log_level(level):
    global GLOBAL_LOG_LEVEL
    GLOBAL_LOG_LEVEL = level


def get_log_level():
    return GLOBAL_LOG_LEVEL


def LOG(code, message, is_check=False):
    """ Send message to stderr. Exit if using code FATAL. """
    if GLOBAL_LOG_LEVEL == 0 or (GLOBAL_LOG_LEVEL > code and code != FATAL):
        return
    if code == FATAL:
        try:
            raise LogFatalException(message, is_check)
        except LogFatalException as e:
            sys.stderr.write("%s\n" % (e))
            sys.stderr.write("Stack trace:\n")
            pretty_print_stacktrace(traceback.extract_stack()[:-1])
            sys.stderr.write("Exited\n")
            sys.exit(6)
    elif GLOBAL_LOG_LEVEL >= code:
        if code == INFO:
            try:
                raise LogInfoException(message)
            except LogInfoException as e:
                sys.stderr.write("%s\n" % (e))
        elif code == DEBUG:
            try:
                raise LogDebugException(message)
            except LogDebugException as e:
                sys.stderr.write("%s\n" % (e))


def CHECK(condition, message=None):
    """ Exit with message if condition is False """
    if not condition:
        if message is None:
            message = "Check failed."
        LOG(FATAL, message, is_check=True)


def CHECK_EQ(obj1, obj2, message=None):
    """ Exit with message if obj1 != obj2. """
    if obj1 != obj2:
        if message is None:
            message = "check failed: %s != %s" % (str(obj1), str(obj2))
        LOG(FATAL, message, is_check=True)


def CHECK_NE(obj1, obj2, message=None):
    """ Exit with message if obj1 == obj2. """
    if obj1 == obj2:
        if message is None:
            message = "check failed: %s == %s" % (str(obj1), str(obj2))
        LOG(FATAL, message, is_check=True)


def CHECK_LE(obj1, obj2, message=None):
    """ Exit with message if not (obj1 <= obj2). """
    if obj1 > obj2:
        if message is None:
            message = "check failed: %s > %s" % (str(obj1), str(obj2))
        LOG(FATAL, message, is_check=True)


def CHECK_GE(obj1, obj2, message=None):
    """ Exit with message if not (obj1 >= obj2)
    """
    if obj1 < obj2:
        if message is None:
            message = "check failed: %s < %s" % (str(obj1), str(obj2))
        LOG(FATAL, message, is_check=True)


def CHECK_LT(obj1, obj2, message=None):
    """ Exit with message if not (obj1 < obj2). """
    if obj1 >= obj2:
        if message is None:
            message = "check failed: %s >= %s" % (str(obj1), str(obj2))
        LOG(FATAL, message, is_check=True)


def CHECK_GT(obj1, obj2, message=None):
    """ Exit with message if not (obj1 > obj2). """
    if obj1 <= obj2:
        if message is None:
            message = "check failed: %s <= %s" % (str(obj1), str(obj2))
        LOG(FATAL, message, is_check=True)


def CHECK_NOTNONE(obj, message=None):
    """ Exit with message is obj is None """
    if obj is None:
        if message is None:
            message = "Check failed. Object is None."
        LOG(FATAL, message, is_check=True)


def format_msg(type_flag, time, module, line_num, message):
    pid = os.getpid()
    output = "[%s%s %d  %s:%d] %s" % (type_flag,
                                      time.strftime("%m%d%y %H:%M:%S.%f"), pid,
                                      os.path.basename(module),
                                      line_num, message)
    return output


class LogDebugException(Exception):

    """ Print a debug log message in glog format. """

    def __init__(self, message):
        stack = traceback.extract_stack()
        self.module = stack[-3][0]
        self.line_number = stack[-3][1]
        self.message = message
        self.time = datetime.datetime.now()
        pretty_print_stacktrace(stack)
        return

    def __str__(self):
        return format_msg("I", self.time, self.module, self.line_number, 
                          self.message)


class LogInfoException(Exception):

    """ Print a info log message in glog format. """

    def __init__(self, message):
        stack = traceback.extract_stack()
        self.module = stack[-3][0]
        self.line_number = stack[-3][1]
        self.message = message
        self.time = datetime.datetime.now()

    def __str__(self):
        return format_msg("I", self.time, self.module, self.line_number, 
                          self.message)


class LogFatalException(Exception):

    """ Print a fatal log message in glog format. """

    def __init__(self, message, isCheck):
        stack = traceback.extract_stack()
        stack_depth = -3
        if isCheck:
            stack_depth = -4
        self.module = stack[stack_depth][0]
        self.line_number = stack[stack_depth][1]
        self.message = message
        self.time = datetime.datetime.now()

    def __str__(self):
        return format_msg("F", self.time, self.module, self.line_number, 
                          self.message)


def pretty_print_stacktrace(stack):
    """ Print a stack trace with nice formatting. """
    for i, f in enumerate(stack):
        name = os.path.basename(f[0])
        line = "\t@\t%s:%d\t%s\n" % (name + "::" + f[2], f[1], f[3])
        sys.stderr.write(line)
