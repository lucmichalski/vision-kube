wget http://luarocks.org/releases/luarocks-${LUAROCKS_VERSION}.tar.gz && \
  tar xzvf luarocks-${LUAROCKS_VERSION}.tar.gz && \
  cd luarocks-${LUAROCKS_VERSION} && \
  ./configure --prefix=/usr/local/openresty/luajit/ --with-lua=/usr/local/openresty/luajit/ --lua-suffix=jit-2.1.0-alpha --with-lua-include=/usr/local/openresty/luajit/include/luajit-2.1/ && \
  make build && \
  make install && \
  cd .. && \
  rm -r ./luarocks-${LUAROCKS_VERSION}* && \
  /usr/local/openresty/luajit/bin/luarocks install lapis && \
  /usr/local/openresty/luajit/bin/luarocks install moonscript && \
  /usr/local/openresty/luajit/bin/luarocks install lapis-console
