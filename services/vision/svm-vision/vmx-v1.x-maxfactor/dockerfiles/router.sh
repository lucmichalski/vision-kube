#!/bin/bash
mkdir -p ./output/
CHECK_TIME=$(date -d "today" +"%Y%m%d%H%M")
TEMP_BACKEND_PATH=./output
rm backends_tmp.toml
rm frontends_tmp.toml
rm backends.toml
rm frontends.toml
echo "# rules"  >> backends_tmp.toml
echo "[backends]"  >> backends_tmp.toml
echo -e "  [backends.backend-3000]" >> backends_tmp.toml
echo -e "     [backends.backend-3000.LoadBalancer]" >> backends_tmp.toml
echo -e '      method = "drr"' >> backends_tmp.toml
echo -e "     [backends.backend-3000.servers.server3000]" >> backends_tmp.toml
echo -e "     url = \"http://127.0.0.1:3000\"" >> backends_tmp.toml
echo -e "     weight = 3" >> backends_tmp.toml

echo "[frontends]" >> frontends_tmp.toml
echo -e "  [frontends.frontend-3000]" >> frontends_tmp.toml
echo -e "  backend = \"backend-3000\"" >> frontends_tmp.toml
echo -e "    [frontends.frontend-3000.routes.gui]" >> frontends_tmp.toml
echo -e "    rule = \"PathPrefix\"" >> frontends_tmp.toml
echo -e "    value = \"/ui/\"" >> frontends_tmp.toml
for f in /vmx/sessions/*; do
    if [[ -d $f ]]; then
        PORT_MAPPING=""
        MODEL_NAME=""
        if [ -f $f/url ]; then

            PORT_MAPPING=`cat -u $f/url`
            echo $PORT_MAPPING
        fi
        if [ -f $f/model.json ]; then
            MODEL_NAME=`jq -r '.name' $f/model.json`
            echo $MODEL_NAME
        fi
        if [[ -n "${MODEL_NAME}" && "${PORT_MAPPING}" ]]; then
            mkdir -p $TEMP_BACKEND_PATH/$CHECK_TIME
            TEMP_BACKEND_FILE="$TEMP_BACKEND_PATH/$CHECK_TIME/bck_$MODEL_NAME.toml"
            TEMP_FRONTEND_FILE="$TEMP_BACKEND_PATH/$CHECK_TIME/fo_$MODEL_NAME.toml"
            ADDRESS_PORT=$(echo $PORT_MAPPING | sed 's/0.0.0.0/127.0.0.1/g')
            PORT=$(echo $PORT_MAPPING | sed 's/0.0.0.0://g')
            if [ ! -f $TEMP_BACKEND_FILE ]; then
               echo -e "  [backends.backend-$MODEL_NAME]" >> $TEMP_BACKEND_FILE
               echo -e "     [backends.backend-"$MODEL_NAME".LoadBalancer]" >> $TEMP_BACKEND_FILE
               echo -e '      method = "drr"' >> $TEMP_BACKEND_FILE
            fi
               echo -e "     [backends.backend-"$MODEL_NAME".servers.server"$PORT"]" >> $TEMP_BACKEND_FILE
               echo -e "     url = \"http://$ADDRESS_PORT\"" >> $TEMP_BACKEND_FILE
               echo -e "     weight = 3" >> $TEMP_BACKEND_FILE
           if [ ! -f $TEMP_FRONTEND_FILE ]; then
               echo -e "  [frontends.frontend-"$PORT"]" >> $TEMP_FRONTEND_FILE
               echo -e "  backend = \"backend-"$MODEL_NAME"\"" >> $TEMP_FRONTEND_FILE
               echo -e "    [frontends.frontend-"$PORT".routes."$MODEL_NAME"]" >> $TEMP_FRONTEND_FILE
               echo -e "    rule = \"PathPrefix\"" >> $TEMP_FRONTEND_FILE
               echo -e "    value = \"/"$MODEL_NAME"_"$PORT"\"" >> $TEMP_FRONTEND_FILE
            fi
        fi
    fi
done
cat $TEMP_BACKEND_PATH/$CHECK_TIME/bck_*.toml >> backends_tmp.toml
cat $TEMP_BACKEND_PATH/$CHECK_TIME/fo_*.toml >> frontends_tmp.toml
cat backends_tmp.toml frontends_tmp.toml >> new_rules.toml
mv rules.toml rules_old.toml
mv new_rules.toml rules.toml
