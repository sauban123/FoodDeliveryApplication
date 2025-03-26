package com.example.myapplication.viewmodels

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.Constants
import com.example.myapplication.Utils
import com.example.myapplication.api.ApiUtilities
import com.example.myapplication.models.Orders
import com.example.myapplication.models.Product
import com.example.myapplication.models.Users
import com.example.myapplication.roomdb.CartProductDao
import com.example.myapplication.roomdb.CartProductTable
import com.example.myapplication.roomdb.CartProductsDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow

class UserViewModel(application: Application) : AndroidViewModel(application) {

    //initialization
    val sharedPreferences: SharedPreferences =
        application.getSharedPreferences("MyPref", MODE_PRIVATE)
    val cartProductDao: CartProductDao =
        CartProductsDatabase.getDatabaseInstance(application).cartProductsDao()

    private val _paymentStatus = MutableStateFlow<Boolean>(false)
    val paymentStatus = _paymentStatus

    //room database
    suspend fun insertCartProduct(products: CartProductTable) {
        cartProductDao.insertCartProduct(products)
    }

    fun getAll(): LiveData<List<CartProductTable>> {
        return cartProductDao.getAllCartProducts()
    }

    suspend fun updateCartProduct(products: CartProductTable) {
        cartProductDao.updateCartProduct(products)
    }

    suspend fun deleteCartProduct(productId: String) {
        cartProductDao.deleteCartProduct(productId)
    }


    //firebase
    fun fetchAllProducts(): Flow<List<Product>> = callbackFlow {
        val db = FirebaseDatabase.getInstance().getReference("Admins").child("AllProducts")

        val eventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val products = ArrayList<Product>()
                for (product in snapshot.children) {
                    val prod = product.getValue(Product::class.java)
                    products.add(prod!!)


                }
                trySend(products)

            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }

        }
        db.addValueEventListener(eventListener)

        awaitClose {
            db.removeEventListener(eventListener)
        }
    }

    fun getCategoryProducts(category: String?): Flow<List<Product>> = callbackFlow {
        val db = FirebaseDatabase.getInstance().getReference("Admins")
            .child("ProductCategory/${category}")
        val eventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val products = ArrayList<Product>()
                for (product in snapshot.children) {
                    val prod = product.getValue(Product::class.java)
                    products.add(prod!!)


                }
                trySend(products)

            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }

        }
        db.addValueEventListener(eventListener)

        awaitClose {
            db.removeEventListener(eventListener)
        }

    }

    fun updateItemCount(product: Product, itemCount: Int) {
        FirebaseDatabase.getInstance().getReference("Admins")
            .child("AllProducts/${product.productRandomID}").child("itemCount").setValue(itemCount)
        FirebaseDatabase.getInstance().getReference("Admins")
            .child("ProductCategory/${product.productCategory}/${product.productRandomID}")
            .child("itemCount").setValue(itemCount)
        FirebaseDatabase.getInstance().getReference("Admins")
            .child("ProductType/${product.productType}/${product.productRandomID}")
            .child("itemCount").setValue(itemCount)
    }

    fun saveUserAddress(address: String) {
        FirebaseDatabase.getInstance().getReference("AllUsers").child("Users")
            .child(Utils.getCurrentUserId()).child("userAddress").setValue(address)

    }

    fun getUserAddress(callback: (String?) -> Unit) {
        val db = FirebaseDatabase.getInstance().getReference("AllUsers").child("Users")
            .child(Utils.getCurrentUserId()).child("userAddress")
        db.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val address = snapshot.getValue(String::class.java)
                    callback(address)
                } else {
                    callback(null)
                }

            }


            override fun onCancelled(error: DatabaseError) {
                callback(null)
            }

        })
    }

    fun saveOrderedProducts(orders: Orders) {
        FirebaseDatabase.getInstance().getReference("Admins").child("Orders").child(orders.orderId!!).setValue(orders)

    }

    //shared preferences
    fun savingCartItemCount(itemCount: Int) {
        sharedPreferences.edit().putInt("itemCount", itemCount).apply()
    }

    fun fetchTotolCartItemCount(): MutableLiveData<Int> {
        val totalItemCount = MutableLiveData<Int>()
        totalItemCount.value = sharedPreferences.getInt("itemCount", 0)
        return totalItemCount
    }

    fun saveAddressStatus() {
        sharedPreferences.edit().putBoolean("addressStatus", true).apply()
    }

    fun getAddressStatus(): MutableLiveData<Boolean> {
        val addressStatus = MutableLiveData<Boolean>()
        addressStatus.value = sharedPreferences.getBoolean("addressStatus", false)
        return addressStatus

    }

    // retrofit

    suspend fun checkPayment(headers: Map<String, String>) {
        val res = ApiUtilities.statusAPI.checkStatus(
            headers,
            Constants.MERCHANTID,
            Constants.merchantTransactionId
        )
        _paymentStatus.value = res.body() != null && res.body()!!.success
    }

}