package com.example.maxweatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.maxweatherapp.databinding.ActivityMainBinding
import com.example.maxweatherapp.models.WeatherResponse
import com.example.maxweatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.time.Instant.ofEpochMilli
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var binding: ActivityMainBinding

    private var mProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. " +
                                        "Please enable them as it is mandatory for the app to work.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationaleDialogForPermission()
                    }
                })
                .onSameThread()
                .check()
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {

            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                lat = latitude,
                lon = longitude,
                units = Constants.METRIC_UNIT,
                appid = Constants.APP_ID
            )

            showCustomProgressDialog()
            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        hideProgressDialog()

                        val weatherList: WeatherResponse? = response.body()
                        setupUi(weatherList)
                        Log.i("RESP_RES", "$weatherList")
                    } else {
                        when (response.code()) {
                            400 -> Log.e("RESP_RES", "Error 400: bad connection")
                            404 -> Log.e("RESP_RES", "Error 404: not found")
                            else -> Log.e("RESP_RES", "Something went wrong")
                        }

                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("RESP_RES", "Error!!!: ${t.message.toString()}")
                }
            })

        } else {
            Toast.makeText(
                this,
                "No internet connection",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            Log.i("RESP_RES", "onLocationResult: callback")
            val mLastLocation: Location = locationResult.lastLocation
            val lat = mLastLocation.latitude
            val lon = mLastLocation.longitude

            Log.i("RESP_RES", "lat: $lat, $lon")

            getLocationWeatherDetails(lat, lon)
        }
    }


    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest.create()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        Log.i("RESP_RES", "requestLocationData: request")
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()!!
        )
    }

    private fun showRationaleDialogForPermission() {
        AlertDialog.Builder(this)
            .setMessage(
                "It looks like you turned off permissions required for this feature. " +
                        "It can be enabled under Application Settings"
            )
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        mProgressDialog?.dismiss()
    }

    private fun setupUi(weatherList: WeatherResponse?) {
        if (weatherList != null) {
            for (i in weatherList.weather.indices) {

                with(binding) {
                    with(weatherList) {
                        tvMain.text = weather[i].main
                        tvMainDescription.text = weather[i].description
                        val tempUnit = getTempUnit(application.resources.configuration.toString())
                        val speedUnit = getSpeedUnit(application.resources.configuration.toString())
                        val temp = "${main.temp} $tempUnit"
                        val feelsLike = "${main.feels_like} $tempUnit"
                        val humidity = "${main.humidity} \u0025"
                        val pressure = "${main.pressure / 10} kPa"
                        val windSpeed = "${wind.speed} $speedUnit"
                        tvTemp.text = temp
                        tvFeelsLike.text = feelsLike
                        tvHumidity.text = humidity
                        tvPressure.text = pressure
                        tvSpeed.text = windSpeed
                        tvWidDirection.text = getWindDirection(wind.deg)
                        tvName.text = name
                        tvCountry.text = sys.country
                        tvSunriseTime.text = unixTime(sys.sunrise)
                        tvSunsetTime.text = unixTime(sys.sunset)

                        setupMainIcon(weather[i].icon)

                    }
                }
            }
        }
    }

    private fun getTempUnit(locale: String): String {
        return when (locale) {
            "US" -> "\u2109"
            "LR" -> "\u2109"
            "MM" -> "\u2109"
            "UK" -> "\u2109"
            else -> "\u2103"
        }
    }

    private fun getSpeedUnit(locale: String): String {
        return when (locale) {
            "US" -> "mph"
            "LR" -> "mph"
            "MM" -> "mph"
            "UK" -> "mph"
            else -> "m/s"
        }
    }

    private fun getWindDirection(degree: Double): String {
        return when (degree) {
            in 0.0..45.0 -> "NNE"
            in 45.1..90.0 -> "ENE"
            in 90.1..135.0 -> "ESE"
            in 135.1..180.0 -> "SSE"
            in 180.1..225.0 -> "SSW"
            in 225.1..270.0 -> "WSW"
            in 270.1..315.0 -> "WNW"
            in 315.1..360.0 -> "NNW"
            else -> ""
        }
    }

    private fun setupMainIcon(icon: String) {
        with(binding.ivMain) {
            when (icon) {
                "01d" -> setImageResource(R.drawable.sunny)
                "02d" -> setImageResource(R.drawable.cloud)
                "03d" -> setImageResource(R.drawable.cloud)
                "04d" -> setImageResource(R.drawable.cloud)
                "04n" -> setImageResource(R.drawable.cloud)
                "10d" -> setImageResource(R.drawable.rain)
                "11d" -> setImageResource(R.drawable.storm)
                "13d" -> setImageResource(R.drawable.snowflake)
                "01n" -> setImageResource(R.drawable.cloud)
                "02n" -> setImageResource(R.drawable.cloud)
                "03n" -> setImageResource(R.drawable.cloud)
                "10n" -> setImageResource(R.drawable.cloud)
                "11n" -> setImageResource(R.drawable.rain)
                "13n" -> setImageResource(R.drawable.snowflake)
                else -> setImageResource(R.drawable.sunny)
            }
        }
    }

    private fun unixTime(timex: Long): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val time = ofEpochMilli(timex * 1000L).atZone(ZoneId.systemDefault()).toLocalTime()
            val df = DateTimeFormatter.ofPattern("HH:mm")
            df.format(time)
        } else {
            val date = Date(timex * 1000L)
            val sdf = SimpleDateFormat("HH:mm")
            sdf.timeZone = TimeZone.getDefault()
            sdf.format(date)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_action_refresh -> {
                Log.i("MENU_CL", "onOptionsItemSelected: clicked")
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

}