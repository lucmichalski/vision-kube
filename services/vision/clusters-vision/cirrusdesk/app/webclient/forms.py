#from registration import forms as regforms
from django.contrib.auth.forms  import AuthenticationForm
from django import forms

class FormLogin(AuthenticationForm):
  username = forms.CharField(label=' ',
    widget=forms.TextInput(attrs={'placeholder':'Username'})
  )
  
  password = forms.CharField(label='',
    widget=forms.PasswordInput( attrs={'placeholder':'Password'} )
  )