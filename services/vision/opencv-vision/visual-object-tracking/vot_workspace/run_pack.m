% This script can be used to pack the results and submit them to a challenge.

[sequences, experiments] = vot_environment();

tracker = create_tracker('meanTracker');

vot_pack(tracker, sequences, experiments);

