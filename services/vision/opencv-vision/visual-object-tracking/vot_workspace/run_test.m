% This script can be used to test the integration of a tracker to the
% framework.

[sequences, experiments] = vot_environment();

tracker = create_tracker('meanTracker');

vot_test(tracker, sequences);

