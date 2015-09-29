while (( "$#" )); do

  fleetctl start vmxserver@${1}.service vmxserver-discovery@${1}.service

shift

done
