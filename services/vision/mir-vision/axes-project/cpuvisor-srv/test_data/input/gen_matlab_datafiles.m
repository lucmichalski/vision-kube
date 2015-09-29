% generates sample input data and output feature text files
% for comparing output of C++ CaffeEncoder with Matlab generated features

im = single(im);

if size(im, 1) < size(im, 2)
    im = imresize(im, [CROPPED_DIM NaN]);
else
    im = imresize(im, [NaN CROPPED_DIM]);
end

% RGB -> BGR
im = im(:, :, [3 2 1]);

centre_crop = ...
    permute(im(center_y:center_y+CROPPED_DIM-1,center_x:center_x+CROPPED_DIM-1,:), ...
            [2 1 3]);

centre_crop_flat = centre_crop(:);

fid = fopen('mlab_input_im.txt','w');
for i = 1:length(centre_crop_flat)
    fprintf(fid,'%f\n', centre_crop_flat(i));
end

norm_scores = scores / norm(scores);

fid = fopen('mlab_output_feat.txt','w');
for i = 1:length(norm_scores)
    fprintf(fid,'%f\n', norm_scores(i));
end
