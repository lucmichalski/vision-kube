import sys

if __name__ == '__main__':
    if len(sys.argv) < 4:
        print "Usage: script INSCOREFILE OUTSCOREFILE"
        sys.exit(1)
    scorefile = sys.argv[1]
    outscorefile = sys.argv[2]
    dataset = sys.argv[3]
    
    f = None
    try:
        f = open(scorefile)
    except:
        print "Couldn't open file"        
        sys.exit(1)
        
    fout = open(outscorefile, 'w')

    lines = f.read().splitlines()
    for i in range(len(lines)):
        line = lines[i].split(' ')
        
        video = ' '.join(line[2:])
        shot = int(line[1])
        score = float(line[0])
        
        video = video.split('.')
        video = video[:-1]
        video = '.'.join(video)
        formattedLine = '%f /%s/s%09d\n' % (score, video, shot)
        
        fout.write(formattedLine)
    
    fout.close()
        
