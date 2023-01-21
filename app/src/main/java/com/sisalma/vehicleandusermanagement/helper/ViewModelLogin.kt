package com.sisalma.vehicleandusermanagement.helper

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sisalma.vehicleandusermanagement.model.API.LoginBody
import com.sisalma.vehicleandusermanagement.model.API.LoginRepoResponse
import com.sisalma.vehicleandusermanagement.model.API.LoginRepository
import com.sisalma.vehicleandusermanagement.model.API.LoginResponse


class ViewModelLogin(application: Application) : AndroidViewModel(application) {
    private var username = ""
    private var password = ""
    val _currentUser: MutableLiveData<LoginBody> = MutableLiveData<LoginBody>()
    val currentUser : LiveData<LoginBody> get()= _currentUser
    private val loginRepo = LoginRepository(getApplication(),viewModelScope,)
    private val _status: MutableLiveData<LoginResponseState?> = MutableLiveData()
    val status: LiveData<LoginResponseState?> get() = _status

    private val _response: MutableLiveData<String> = MutableLiveData()
    val response : LiveData<String> get()= _response

    fun setCurrentUser(username: String, password: String) {
        this.username = username
        this.password = password
    }

    fun setResponse(response: LoginRepoResponse){
        when(response) {
            is LoginRepoResponse.LoginSuccess -> {
                _response.value = response.result
                _status.value  = LoginResponseState.successLogin()
                Log.i("ViewModelLoginInternalS",response.toString())
            }
            is LoginRepoResponse.SignupSuccess -> {
                _response.value = response.result
                _status.value  = LoginResponseState.successSignup()
            }
        }
    }

    fun loginAction() {
        if(this.username.isNotBlank() and this.password.isNotBlank()) {
            _response.value = "Waiting..."
            val test = LoginBody("login",this.username,this.password,null)
            Log.i("ViewModelLoginInternal",test.intent+
                    " User: "+
                    test.username)
            _currentUser.value = test
        }else{
            _response.value = "Fill in user and password"
        }
    }

    fun daftarAction(){
        if(this.username.isNotBlank() and this.password.isNotBlank()) {
            _currentUser.value = LoginBody("signup",this.username,this.password,null)
        }
    }

    fun errorListener(){
        _status.value = err
    }

    fun clearViewModel(){
        _status.value = null
        _response.value = null
    }
}
sealed class LoginResponseState{
    class successLogin: LoginResponseState()
    class errorLogin(val errorMsg: String): LoginResponseState()
    class successSignup: LoginResponseState()
    class errorSignup(val errorMsg: String): LoginResponseState()
}