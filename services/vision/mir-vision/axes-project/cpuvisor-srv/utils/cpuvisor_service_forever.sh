CRASH_LOG=../logs/cpuvisor_service_crash.log
STDERR_LOG=../logs/cpuvisor_service_stderr.log

# NOTE: full logfiles are also stored by google logging at /tmp/cpuvisor_service.*.log.* by default

if [ ! -d ../logs ]
then
    mkdir -p ../logs
fi

cd ../bin

until ./cpuvisor_service "$@" 1>&1 2> >(tee -a $STDERR_LOG >&2); do
    ERR_CODE=$?
    echo "Server './cpuvisor_service' crashed with exit code $ERR_CODE.  Respawning..." >&2

    now=$(date "+%Y-%m-%d %H:%M:%S")
    echo "$now: Exited with code $ERR_CODE " >> $CRASH_LOG

    sleep 1
done
