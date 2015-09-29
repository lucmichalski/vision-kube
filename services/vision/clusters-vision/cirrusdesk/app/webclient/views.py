from django.http import HttpResponse
from django.contrib import messages
from django.shortcuts import render
from django.contrib.auth.decorators import login_required
from django import forms
from django.http import HttpResponseRedirect
#import multiprocessing
from cirruscluster import core
from cirruscluster import workstation

import models
from boto import exception

#worker_pool = None
#
#def GetWorkerPool():
#  global worker_pool
#  if not worker_pool:
#    worker_pool = multiprocessing.Pool(processes=20)  # start worker processes
#  return worker_pool

def GetManager(request):
  iam_credentials = request.user.iamcredentials
  region = 'us-east-1'
  manager = workstation.Manager(region, iam_credentials.iam_key_id,
                                iam_credentials.iam_key_secret)
  return manager

def Index(request):
  context = {}
  return render(request, 'index.html', context)
  

@login_required(login_url='/accounts/login/')
def Workstations(request):
  # check if iam credentials are in DB
  iam_credentials = None
  try: 
   iam_credentials = request.user.iamcredentials
  except:
    pass
  
  # get IAM credentials, if needed using root AWS credentials
  if not iam_credentials:
    return HttpResponseRedirect('/setup_credentials') # Redirect after POST

  try:
    manager = GetManager(request)
  except workstation.InvalidAwsCredentials:
    return HttpResponseRedirect('/setup_credentials') # Redirect after POST
  instances = manager.ListInstances()
  context = {'instances': instances}
  return render(request, 'workstations.html', context)


@login_required(login_url='/accounts/login/')
def Stop(request, instance_id):
  instance_id = instance_id.encode('ascii', 'ignore')
  GetManager(request).StopInstance(instance_id)  
  return HttpResponseRedirect('/workstations')

@login_required(login_url='/accounts/login/')
def Start(request, instance_id):
  instance_id = instance_id.encode('ascii', 'ignore')
  GetManager(request).StartInstance(instance_id)  
  return HttpResponseRedirect('/workstations')


@login_required(login_url='/accounts/login/')
def Connect(request, instance_id):
  instance_id = instance_id.encode('ascii', 'ignore')
  manager = GetManager(request)
  conn_config_data = manager.CreateRemoteSessionConfig(instance_id)
  response = HttpResponse(conn_config_data, content_type='application/nx-session')
  info = manager.GetInstanceInfo(instance_id)
  safe_name = info.name
  safe_name = safe_name.replace('_', '-')
  safe_name = safe_name.replace('=', '-')
  safe_name = safe_name.replace(',', '-')
  response['Content-Disposition'] = 'attachment; filename="%s.nxs"' % (safe_name)
  response.set_cookie('fileDownload', 'true')  
  return response


class DestroyConfirmForm(forms.Form):
  confirm = forms.CharField(max_length=100, label="")
  
  def clean_confirm(self):
    data = self.cleaned_data['confirm']
    if data != "destroy":
      raise forms.ValidationError("You must enter 'destroy' to confirm destruction of this workstation.")
    return data
  
  
@login_required(login_url='/accounts/login/')
def Destroy(request, instance_id):
  form = DestroyConfirmForm() # An unbound form
  instance_id = instance_id.encode('ascii', 'ignore')
  if request.method == 'POST': # If the form has been submitted...
    form = DestroyConfirmForm(request.POST) # A form bound to the POST data
    if form.is_valid(): # All validation rules pass
      manager = GetManager(request)
      manager.TerminateInstance(instance_id)
      return HttpResponseRedirect('/workstations/') # Redirect after POST
  
  return render(request, 'destroy_workstation.html', {'instance_id': instance_id, 'form': form,})          



class AddStorageForm(forms.Form):
  new_size_gb = forms.DecimalField(max_digits=4, min_value=10, max_value=500, decimal_places=0, label="Desired new size in GB")
  
  def clean_new_size_gb(self):
    data = self.cleaned_data['new_size_gb']
    # TODO raise validation exception if new size is smaller than current size (only expanding is currently supported)
    return data
  
  
@login_required(login_url='/accounts/login/')
def AddStorage(request, instance_id):
  form = AddStorageForm() # An unbound form
  instance_id = instance_id.encode('ascii', 'ignore')
  if request.method == 'POST': # If the form has been submitted...
    form = AddStorageForm(request.POST) # A form bound to the POST data
    if form.is_valid(): # All validation rules pass
      manager = GetManager(request)
      new_size_gb = int(form.cleaned_data['new_size_gb'])
      manager.ResizeRootVolumeOfInstance(instance_id, new_size_gb)
      return HttpResponseRedirect('/workstations/') # Redirect after POST
  
  return render(request, 'add_storage.html', {'instance_id': instance_id, 'form': form,})          



class SetupAwsCredentialsForm(forms.Form):
  aws_key_id = forms.CharField(min_length=20, max_length=20, label='', widget=forms.TextInput(attrs={'placeholder':'Key ID', 'size': 20}), help_text='&nbsp; <span style="font-size: 10px; color: gray;">Example: AKIBJXJDW89GBT46DMGA</span>')
  aws_key_secret = forms.CharField(min_length=40, max_length=40, label='', widget=forms.TextInput(attrs={'placeholder':'Key Secret', 'size': 40}), help_text='&nbsp; <span style="font-size: 10px; color: gray;">Example: q73vQ2T5rGe6bcrRTfTXyaZgrdT/53WVdysqLoYx</span>')
 
  def clean(self):
    cleaned_data = super(SetupAwsCredentialsForm, self).clean()
    aws_key_id = cleaned_data.get("aws_key_id")
    aws_key_secret = cleaned_data.get("aws_key_secret")
    
    if aws_key_id and aws_key_secret:
      if not core.CredentialsValid(aws_key_id, aws_key_secret):
        raise forms.ValidationError("Invalid AWS Access Key")
    # Always return the full collection of cleaned data.
    return cleaned_data

def SetupAwsCredentials(request):
  form = SetupAwsCredentialsForm() # An unbound form
  key_id = None
  key_secret = None
  if request.method == 'POST': # If the form has been submitted...
    form = SetupAwsCredentialsForm(request.POST) # A form bound to the POST data
    if form.is_valid(): # All validation rules pass
      # Process the data in form.cleaned_data
      root_aws_id = form.cleaned_data['aws_key_id']
      root_aws_secret = form.cleaned_data['aws_key_secret']
      key_id, key_secret = workstation.GetCirrusIamUserCredentials(root_aws_id, 
                                                         root_aws_secret)
      
      #iam_credentials = models.IamCredentials.objects.get(user=request.user)
      iam_credentials, created = models.IamCredentials.objects.get_or_create(user=request.user)
      iam_credentials.iam_key_id = key_id
      iam_credentials.iam_key_secret = key_secret
      iam_credentials.save()                  
      return HttpResponseRedirect('/workstations') # Redirect after POST  
  return render(request, 'setup_credentials.html', {'form': form,})   


class CreateWorkstationForm(forms.Form):
  name = forms.CharField(label=" ",
                         max_length=100,
                         min_length = 3,
                         widget=forms.TextInput(attrs={'placeholder': 'workstation name' }), 
                         help_text=u'Name for the new workstation.',)
  INSTANCE_TYPE_CHOICES = (
    ('c1.medium', 'c1.medium'),
    ('c1.xlarge', 'c1.xlarge'),  
  )
  instance_type = forms.ChoiceField(choices=INSTANCE_TYPE_CHOICES)

class CreateWorkstationRunner():
  def __init__(self, manager, name, instance_type, ubuntu_release_name, 
               mapr_version, ami_release_name, ami_owner_id):
    self.manager = manager
    self.name = name
    self.instance_type = instance_type
    self.ubuntu_release_name = ubuntu_release_name
    self.mapr_version = mapr_version
    self.ami_release_name = ami_release_name
    self.ami_owner_id = ami_owner_id
    return
  
  def __call__(self):
    self.manager.CreateInstance(self.name, self.instance_type, 
                                self.ubuntu_release_name, 
                                self.mapr_version, self.ami_release_name, 
                                self.ami_owner_id)
    return
    
 
  
def CreateWorkstation(request):
  #form = CreateWorkstationForm(initial={'name': 'my_workstation', 'instance_type': 'c1.xlarge'}) # An unbound form
  form = CreateWorkstationForm() # An unbound form
  
  if request.method == 'POST': # If the form has been submitted...
    form = CreateWorkstationForm(request.POST) # A form bound to the POST data
    if form.is_valid(): # All validation rules pass
      # Process the data in form.cleaned_data
      name = form.cleaned_data['name']
      instance_type = form.cleaned_data['instance_type']
      manager = GetManager(request)
      ubuntu_release_name = 'precise'
      mapr_version = 'v2.1.3'
      
      messages.success(request, 'Your new workstation "%s" is starting up...' % (name))
      manager.CreateInstance(name, 
                             instance_type,
                             ubuntu_release_name, 
                             mapr_version,
                             core.default_ami_release_name, 
                             core.default_ami_owner_id)
      return HttpResponseRedirect('/workstations/') # Redirect after POST
  
  return render(request, 'create_workstation.html', {'form': form,})      

#def CreateWorkstation(request):
#  form = CreateWorkstationForm(initial={'name': 'my_workstation', 'instance_type': 'c1.xlarge'}) # An unbound form
#  
#  if request.method == 'POST': # If the form has been submitted...
#    form = CreateWorkstationForm(request.POST) # A form bound to the POST data
#    if form.is_valid(): # All validation rules pass
#      # Process the data in form.cleaned_data
#      name = form.cleaned_data['name']
#      instance_type = form.cleaned_data['instance_type']
#      manager = GetManager(request)
#      ubuntu_release_name = 'precise'
#      mapr_version = 'v2.1.3'
#      success = False      
#      
#      try:
#        manager.CreateInstance(name, 
#                               instance_type,
#                               ubuntu_release_name, 
#                               mapr_version,
#                               core.default_ami_release_name, 
#                               core.default_ami_owner_id)
#        success = True
#      except RuntimeError as e:
#        messages.error(request, '%s' % (e))
#      
#      if success:
#        messages.success(request, 'A new workstation was created: %s' % (name))
#        
#      return HttpResponseRedirect('/workstations/') # Redirect after POST
#  
#  return render(request, 'create_workstation.html', {'form': form,})    



