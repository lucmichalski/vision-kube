def binaryInPath(cmdname):
  import os
  for path_dir in os.environ["PATH"].split(os.pathsep):
    path_dir = path_dir.strip('"')
    full_path = os.path.join(path_dir, cmdname)
    if os.path.isfile(full_path) and os.access(full_path, os.X_OK):
      return True
  return False

