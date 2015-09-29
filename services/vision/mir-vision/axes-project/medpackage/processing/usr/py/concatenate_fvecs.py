import sys
import numpy as np
from yael import ynumpy


def usage():
    print "\n   usage: (python) concatenate_fvecs.py --infisherdir DIR --infilename FORMAT --outfilename FORMAT --videolist FILE\n"
    print "'infisherdir' contains directories listed in 'videolist'\n"

def main():
	infisherdir = ""
	infilename = ""
	outfilename = ""
	videolist = ""
	
	args=sys.argv[1:]
	while args:
		a=args.pop(0)
		if a in ('-h','--help'):
			usage()
			sys.exit(0)
		elif a=='--infisherdir':
			infisherdir = args.pop(0)
		elif a=='--infilename':
			infilename = args.pop(0)
		elif a=='--outfilename':
			outfilename = args.pop(0)
		elif a=='--videolist':
			videolist = args.pop(0)
		else:
			print "unknown option", a
			usage()
			sys.exit(1)

	if infisherdir == "" or infilename == "" or outfilename == "" or videolist == "":
		usage()
		print "err: missing options"
		sys.exit(1)

	lines = [line.strip() for line in open(videolist)]
	it = 1
	total = len(lines)
	a = []
	for i in lines:
		print i, it, "/", total
		a.append(ynumpy.fvecs_read(infisherdir + "/" + i + "/" + infilename)[0])
		it += 1
	
	x = np.vstack(a)
	ynumpy.fvecs_write(outfilename, x)
	
if __name__ == '__main__':
    main()
