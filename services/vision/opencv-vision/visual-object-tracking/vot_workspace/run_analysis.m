% This script can be used to perform a comparative analyis of the experiments
% in the same manner as for the VOT challenge
% You can copy and modify it to create a different analyis

[sequences, experiments] = vot_environment();

%error('Analysis not configured!'); % Remove this line after proper configuration

trackers = create_trackers('meanTracker', 'TODO'); % TODO: add more trackers here

context = create_report_context('report_vot2014_meanTracker');

report_article(context, experiments, trackers, sequences, 'spotlight', 'meanTracker'); % This report is more suitable for results included in a paper

% report_challenge(context, experiments, trackers, sequences); % Use this report for official challenge report

