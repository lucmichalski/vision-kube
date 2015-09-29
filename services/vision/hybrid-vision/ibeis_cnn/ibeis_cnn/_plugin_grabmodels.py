from __future__ import absolute_import, division, print_function
import utool as ut
(print, print_, printDBG, rrr, profile) = ut.inject(
    __name__, '[ibeis_cnn._plugin_grabmodels]', DEBUG=False)


#DEFAULT_CNNMODELS_DIR = ut.get_app_resource_dir('ibeis_cnn', 'pretrained')

MODEL_URLS = {
    'viewpoint':     'https://www.dropbox.com/s/6xjtcz8qrdj2cof/viewpoint.pickle?dl=0',
    'caffenet':      'https://www.dropbox.com/s/6sn5eh53jh79p4e/caffenet.caffe.slice_0_6_None.pickle?dl=0',
    'caffenet_conv': 'https://www.dropbox.com/s/4u8g2n2t271vosv/caffenet.caffe.slice_0_10_None.pickle?dl=0',
    'caffenet_full': 'https://www.dropbox.com/s/r9oaif5os45cn2s/caffenet.caffe.pickle',
    'vggnet':        'https://www.dropbox.com/s/vps5m2fbvl6y1jb/vgg.caffe.slice_0_6_None.pickle?dl=0',
    'vggnet_conv':   'https://www.dropbox.com/s/s29k06buhbojtss/vgg.caffe.slice_0_32_None.pickle?dl=0',
    'vggnet_full':   'https://www.dropbox.com/s/i7yb2ogmzr3w7v5/vgg.caffe.pickle?dl=0',
}


def ensure_model(model, redownload=False):
    try:
        url = MODEL_URLS[model]
        extracted_fpath = ut.grab_file_url(url, appname='ibeis_cnn', redownload=redownload)
    except KeyError as ex:
        ut.printex(ex, 'model is not uploaded', iswarning=True)
        extracted_fpath = ut.unixjoin(ut.get_app_resource_dir('ibeis_cnn'), model)
        ut.assert_exists(extracted_fpath)
    return extracted_fpath


if __name__ == '__main__':
    """

    CommandLine:
        python -m ibeis_cnn._plugin_grabmodels.ensure_models
    """
    import multiprocessing
    multiprocessing.freeze_support()  # for win32
    import utool as ut  # NOQA
    ut.doctest_funcs()
