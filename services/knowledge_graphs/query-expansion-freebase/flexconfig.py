# author: Erik Rodner
# with code from:
# http://stackoverflow.com/questions/3609852/which-is-the-best-way-to-allow-configuration-options-be-overridden-at-the-comman

import argparse
import ConfigParser
import sys
import os

def get_parser(stdconfigfile = "config.conf"):
    # Parse any config (file) specification
    # We make this parser with add_help=False so that
    # it doesn't parse -h and print help.
    conf_parser = argparse.ArgumentParser(
          description=__doc__, # printed with -h/--help
          # Don't mess with format of description
          formatter_class=argparse.RawDescriptionHelpFormatter,
          # Turn off help, so we print all options in response to -h
          add_help=False
        )

    conf_parser.add_argument("-c", "--config",
                        help="Specify config file", metavar="FILE", default=os.path.expanduser(stdconfigfile) )
    args, remaining_argv = conf_parser.parse_known_args()

    defaults = {}
    if args.config:
        config = ConfigParser.SafeConfigParser()
        try:
          config.read([args.config])
          defaults = dict(config.items("main")) 
        except (ConfigParser.NoSectionError, IOError):
          pass

    # Parse rest of arguments
    # Don't suppress add_help here so it will handle -h
    parser = argparse.ArgumentParser(
        # Inherit options from config_parser
        parents=[conf_parser]
    )
    parser.set_defaults(**defaults)

    return parser

