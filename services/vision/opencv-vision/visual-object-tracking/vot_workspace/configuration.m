
set_global_variable('workspace_path', fileparts(mfilename('fullpath')));

set_global_variable('version', 2);

% Enable more verbose output
% set_global_variable('debug', 1);
set_global_variable('trax_client','/usr/local/bin/traxclient');

% Disable result caching
% set_global_variable('cache', 0);

% Disable result packaging
% set_global_variable('pack', 0);

% Select experiment stack
set_global_variable('stack', 'vot2014');
