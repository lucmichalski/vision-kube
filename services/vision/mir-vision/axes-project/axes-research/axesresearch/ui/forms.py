# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from django import forms
from django.contrib.auth.models import User
from django.contrib.auth.forms import UserCreationForm
from django.conf import settings
from django.utils.translation import ugettext, ugettext_lazy as _

class UserRegistrationForm(UserCreationForm):
    error_messages = {
        'duplicate_username': _("A user with that username already exists."),
        'bad_access_code': _("Invalid registration access code."),
        'password_mismatch': _("Passwords do not match")
    }
    
    email = forms.EmailField(label="Email", 
        help_text="Required. A valid email address.")
    first_name = forms.CharField(label="First name",
        help_text="Required.")
    last_name = forms.CharField(label="Last name",
        help_text="Required.")
        
    class Meta:
        model = User
        fields = ("username", "first_name", "last_name", "email", )
    
    def save(self, commit=True):
        user = super(UserRegistrationForm, self).save(commit=False)
        user.first_name = self.cleaned_data["first_name"]
        user.last_name = self.cleaned_data["last_name"]
        user.email = self.cleaned_data["email"]
        if commit:
            user.save()
        return user

class ProtectedUserRegistrationForm(UserRegistrationForm):
    access_code = forms.CharField(widget=forms.PasswordInput(), 
        help_text="Required. Access code provided for registration.")
    
    def clean_access_code(self):
        access_code = self.cleaned_data["access_code"]
        if access_code != settings.REGISTRATION_ACCESS_CODE:
            raise forms.ValidationError(
                self.error_messages['bad_access_code'],
                code='bad_access_code')
        return access_code

class UploadFileForm(forms.Form):
    image = forms.FileField()
