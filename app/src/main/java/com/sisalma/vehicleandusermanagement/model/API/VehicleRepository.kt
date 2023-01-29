package com.sisalma.vehicleandusermanagement.model.API

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.haroldadmin.cnradapter.NetworkResponse
import com.sisalma.vehicleandusermanagement.helper.ErrorType
import com.sisalma.vehicleandusermanagement.helper.ViewModelError
import com.sisalma.vehicleandusermanagement.helper.vehicleOperationRequest
import com.sisalma.vehicleandusermanagement.model.BLEStuff.bluetoothLEService
import com.sisalma.vehicleandusermanagement.model.BluetoothResponse
import com.sisalma.vehicleandusermanagement.model.bluetoothLEDeviceFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VehicleRepository(context: Application,activity: AppCompatActivity,ViewModelError: ViewModelError, BLEService: bluetoothLEService?, BLEFinder: bluetoothLEDeviceFinder?) {
    private val BLEScanner: bluetoothLEDeviceFinder?  = BLEFinder
    private val conteks = context
    private val activity = activity
    private val endPointService = APIEndpoint.getInstance(context).vehicleService
    private val errorView = ViewModelError
    private var _responseMember: MutableLiveData<ListMemberData> = MutableLiveData()
    val responseMember: LiveData<ListMemberData> get() = _responseMember
    private var _responseStatus: MutableLiveData<opResult> = MutableLiveData()
    val responseStatus: LiveData<opResult> get() = _responseStatus

    fun requestParser(inputRequest: vehicleOperationRequest){
        when(inputRequest){
            is vehicleOperationRequest.getVehicleMember->getVehicleSummary(inputRequest.VID)
            is vehicleOperationRequest.addMember->addFriend(inputRequest.VID,inputRequest.members)
            is vehicleOperationRequest.removeMember->removeFriend(inputRequest.VID,inputRequest.members)
            is vehicleOperationRequest.transferVehicle->transferOwner(inputRequest.VID,inputRequest.targetMember)
            is vehicleOperationRequest.bluetoothConnectRequest->findFromScannedList(inputRequest.VID)
            is vehicleOperationRequest.bluetoothSearchRequest->findNearbyDevice()
        }
    }
    fun addFriend(VID: Int,UIDTarget: ListMemberData){
        val form = arrayListOf<ChangeMemberForm>()
        UIDTarget.VehicleMember.forEach{
            form.add(ChangeMemberForm(it.UID,VID))
        }.let { runAddFriend(GroupBody("add", form)) }
    }

    fun removeFriend(VID: Int,UIDTarget: ListMemberData){
        val form = arrayListOf<ChangeMemberForm>()
        UIDTarget.VehicleMember.forEach{
            form.add(ChangeMemberForm(it.UID,VID))
        }.let { runRemoveFriend(GroupBody("delete", form)) }
    }

    fun transferOwner(VID: Int, UIDTarget: MemberData){
        val form = ChangeMemberForm(UIDTarget.UID,VID)
        runTransferOwnership(GroupBody("transfer", arrayListOf(form)))
    }

    fun getVehicleSummary(VID: Int){
        runGetVehicleMember(GroupBody("member", arrayListOf(ChangeMemberForm(0,VID))))
    }

    fun findFromScannedList(deviceName: String){
        activity.lifecycleScope.launch(Dispatchers.IO){
            BLEScanner?.scanLeDevice()
            BLEScanner?.findLEDevice(deviceName)
        }
    }

    fun findNearbyDevice(){
        activity.lifecycleScope.launch(Dispatchers.IO){
            BLEScanner?.scanLeDevice()?.let {
                bluetoothErrorHandler(it)?.let {

                }
            }
        }
    }

    private fun runAddFriend(actionBody: GroupBody){
        activity.lifecycleScope.launch(Dispatchers.IO){
            val result = endPointService.addFriend(actionBody)
            connectionErrorHandler(result)?.let { OKResponse ->
                when(OKResponse){
                    is NetworkResponse.Success-> _responseStatus.postValue(opResult.addSuccess())
                    is NetworkResponse.Error-> _responseStatus.postValue(opResult.addError(OKResponse.body!!.errMsg))
                }
            }
        }
    }
    private fun runRemoveFriend(actionBody: GroupBody){
        activity.lifecycleScope.launch(Dispatchers.IO){
            val result = endPointService.removeFriend(actionBody)
            connectionErrorHandler(result)?.let { OKResponse ->
                when(OKResponse){
                    is NetworkResponse.Success-> _responseStatus.postValue(opResult.removeSuccess())
                    is NetworkResponse.Error-> _responseStatus.postValue(opResult.removeError(OKResponse.body!!.errMsg))
                }
            }
        }
    }
    private fun runTransferOwnership(actionBody: GroupBody){
        activity.lifecycleScope.launch(Dispatchers.IO){
            val result = endPointService.transferOwnership(actionBody)
            connectionErrorHandler(result)?.let { OKResponse ->
                when(OKResponse){
                    is NetworkResponse.Success-> _responseStatus.postValue(opResult.addSuccess())
                    is NetworkResponse.Error-> _responseStatus.postValue(opResult.addError(OKResponse.body!!.errMsg))
                }
            }
        }
    }
    private fun runGetVehicleMember(actionBody: GroupBody){
        activity.lifecycleScope.launch(Dispatchers.IO){
            val result = endPointService.getVehicleSummary(actionBody)
            val gson  = Gson()
            connectionErrorHandler(result)?.let { OKResponse ->
                when(OKResponse) {
                    is NetworkResponse.Success -> {
                        val data = gson.fromJson(OKResponse.body.msg, ListMemberData::class.java)
                        _responseMember.postValue(data)
                    }
                }
            }
        }
    }

    private fun connectionErrorHandler(result:NetworkResponse<ResponseSuccess, ResponseError>): NetworkResponse<ResponseSuccess, ResponseError>?{
        var forwardResponse = true
        when (result) {
            is NetworkResponse.ServerError -> {
                result.body?.let {
                    errorView.setError(ErrorType.ShowableError("Server Error: ".plus(result.code.toString()),it.errMsg))
                }
                forwardResponse = false
            }
            is NetworkResponse.NetworkError -> {
                Log.e("Retrofit-Networking", result.error.toString())
                forwardResponse = false
            }
            is NetworkResponse.UnknownError -> {
                Log.e("Retrofit-Unknown", result.error.toString())
                forwardResponse = false
            }
        }
        if(forwardResponse){
            return result
        }
        return null
    }
    private fun bluetoothErrorHandler(result:BluetoothResponse): BluetoothResponse?{
        var forwardResponse = true
        when(result){
            is BluetoothResponse.connectionFailed->{
                errorView.setError(ErrorType.ShowableError("VehicleRepository","Can't estabilish connection to selected macaddress"))
                forwardResponse = false
            }
            is BluetoothResponse.gattFail->{
                errorView.setError(ErrorType.LogableError("VehicleRepository","GATT Server doesn't not respond, might be signal to low"))
                forwardResponse = false
            }
        }
        if (forwardResponse){
            return result
        }
        return null
    }
}

sealed class opResult{
    class addSuccess: opResult()
    class addError(val errorMsg: String): opResult()
    class removeSuccess: opResult()
    class removeError(val errorMsg: String): opResult()
    class transferSuccess: opResult()
    class transferError(val errorMsg: String): opResult()
    class bluetoothSearchSuccess(): opResult()
    class bluetoothSearchFailed(): opResult()
    class btSuccesful(val errorMsg: String): opResult()
    class btFail(val errorMsg: String): opResult()
}

data class ListMemberData(val VehicleMember:List<MemberData>)
data class MemberData(val Username:String, val UID: Int, val AccKey: String)