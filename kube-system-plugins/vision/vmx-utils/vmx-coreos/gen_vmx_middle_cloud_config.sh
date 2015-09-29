MYURL=http:/whate.com
DISCOVERY_URL=$(curl https://discovery.etcd.io/new)
read -r -d '' MYVAR << EOF
#cloud-config

coreos:
  etcd:
    #new url for each cluster: https://discovery.etcd.io/new
    discovery: $DISCOVERY_URL
    addr: \$private_ipv4:4001
    peer-addr: \$private_ipv4:7001
  fleet:
    public-ip: \$private_ipv4   # used for fleetctl ssh command
  units:
    - name: etcd.service
      command: start
    - name: fleet.service
      command: start
    - name: userdata.service
      command: start
      content: |
        Description=VMX User Data
        
        # Requirements
        Requires=etcd.service
        Requires=docker.service
        Requires=appbuilder.service
        Requires=middledata.service
        Requires=middle.service
        
        # Dependency ordering
        After=etcd.service
        After=docker.service
        Before=appbuilder.service
        Before=middledata.service
        Before=middle.service
        
        [Service]
        # Let processes take awhile to start up (for first run Docker containers)
        TimeoutStartSec=0
        
        # work correctly.
        KillMode=none
        
        # Get CoreOS environmental variables
        EnvironmentFile=/etc/environment
        
        # Pre-start and Start
        ExecStartPre=-/usr/bin/docker kill vmxuserdata
        ExecStartPre=-/usr/bin/docker rm vmxuserdata
        ExecStartPre=/usr/bin/docker pull visionai/vmx-userdata
        ExecStart=/usr/bin/docker run -d --name vmxuserdata  visionai/vmx-userdata
        
        # Stop
        ExecStop=/usr/bin/docker stop vmxuserdata

    - name: appbuilder.service
      command: start
      content: |
        Description=VMX App Builder service

        # Requirements
        Requires=etcd.service
        Requires=docker.service
        Requires=userdata.service
        Requires=middledata.service
        Requires=middle.service

        # Dependency ordering
        After=etcd.service
        After=docker.service
        After=userdata.service
        Before=middledata.service
        Before=middle.service

        [Service]
        # Let processes take awhile to start up (for first run Docker containers)
        TimeoutStartSec=0

        # work correctly.
        KillMode=none

        # Get CoreOS environmental variables
        EnvironmentFile=/etc/environment

        # Pre-start and Start
        ExecStartPre=-/usr/bin/docker kill vmxappbuilder
        ExecStartPre=-/usr/bin/docker rm vmxappbuilder
        ExecStartPre=/usr/bin/docker pull visionai/vmx-appbuilder
        ExecStart=/usr/bin/docker run -d --name vmxappbuilder visionai/vmx-appbuilder

        # Stop
        ExecStop=/usr/bin/docker stop vmxappbuilder

        [X-Fleet]
        # Schedule on the same machine as the associated Middle Service
        X-ConditionMachineOf=userdata.service
    - name: middledata.service
      command: start
      content: |
        Description=VMX Middle binaries
        
        # Requirements
        Requires=etcd.service
        Requires=docker.service
        Requires=userdata.service
        Requires=appbuilder.service
        Requires=middle.service
        
        # Dependency ordering
        After=etcd.service
        After=docker.service
        After=userdata.service
        After=appbuilder.service
        Before=middle.service
        
        [Service]
        # Let processes take awhile to start up (for first run Docker containers)
        TimeoutStartSec=0
        
        # work correctly.
        KillMode=none
        
        # Get CoreOS environmental variables
        EnvironmentFile=/etc/environment
        
        # Pre-start and Start
        ExecStartPre=-/usr/bin/docker kill vmxmiddledata
        ExecStartPre=-/usr/bin/docker rm vmxmiddledata
        ExecStartPre=/usr/bin/docker pull visionai/vmx-middle
        ExecStart=/usr/bin/docker run -d --name vmxmiddledata visionai/vmx-middle
        
        # Stop
        ExecStop=/usr/bin/docker stop vmxmiddledata
        [X-Fleet]
        # Schedule on the same machine as the associated Middle Service
        X-ConditionMachineOf=appbuilder.service

    - name: mcr.service
      command: start
      content: |
        Description=Matlab MCR
        
        # Requirements
        Requires=etcd.service
        Requires=docker.service
        
        # Dependency ordering
        After=etcd.service
        After=docker.service
        
        [Service]
        # Let processes take awhile to start up (for first run Docker containers)
        TimeoutStartSec=0
        
        # work correctly.
        KillMode=none
        
        # Get CoreOS environmental variables
        EnvironmentFile=/etc/environment
        
        # Pre-start and Start
        ExecStartPre=-/usr/bin/docker kill vmxmcr
        ExecStartPre=-/usr/bin/docker rm vmxmcr
        ExecStartPre=/usr/bin/docker pull visionai/mcr-2014a
        ExecStart=/usr/bin/docker run -d --name vmxmcr visionai/mcr-2014a
        
        # Stop
        ExecStop=/usr/bin/docker stop vmxmcr

    - name: middle.service
      command: start
      content: |
        Description=VMX Middle Service
        
        # Requirements
        Requires=etcd.service
        Requires=docker.service
        Requires=userdata.service
        Requires=appbuilder.service
        Requires=middledata.service
        
        # Dependency ordering
        After=etcd.service
        After=docker.service
        After=userdata.service
        After=appbuilder.service
        After=middledata.service
        
        [Service]
        # Let processes take awhile to start up (for first run Docker containers)
        TimeoutStartSec=0
        
        # work correctly.
        KillMode=none
        
        # Get CoreOS environmental variables
        EnvironmentFile=/etc/environment
        
        # Pre-start and Start
        ExecStartPre=-/usr/bin/docker kill vmxenvironment
        ExecStartPre=-/usr/bin/docker rm vmxenvironment
        ExecStartPre=/usr/bin/docker pull visionai/vmx-environment
        ExecStart=/usr/bin/docker run -t        --volumes-from vmxmcr            --volumes-from vmxuserdata        --volumes-from vmxmiddledata             --volumes-from vmxappbuilder    -p \${COREOS_PUBLIC_IPV4}:80:3000   --name vmxenvironment          --rm                               visionai/vmx-environment /vmx/middle/vmx 
        # Stop
        ExecStop=/usr/bin/docker stop vmxenvironment
        
        [X-Fleet]
        # Schedule on the same machine as the associated Middle Service
        X-ConditionMachineOf=middledata.service
EOF

echo "$MYVAR"
