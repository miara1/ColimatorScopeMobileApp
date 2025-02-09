package com.example.kalkbp

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.math.pow
import kotlin.math.round

data class BulletData(
    var name: String,
    var grain: Double,
    var diameter: Double,
    var bc: Double,
    var fpsMuzzle: Double,
)

class MainActivity : ComponentActivity() {
    var pos = 0
    var firstOpen = true
    private var contentFrame: FrameLayout? = null
    private var selectedAmmo: String = "22LR 40gr"
    var arraylist = ArrayList<String>()
    var listOfAmmo = ArrayList<BulletData>()

    private val client = OkHttpClient()

    private val PREFS_NAME = "ammo_prefs"
    private val AMMO_LIST_KEY = "ammo_list"

    private var itemsToExport: List<BulletData>? = null

    private fun saveAmmoList() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        val json = Gson().toJson(listOfAmmo)
        editor.putString(AMMO_LIST_KEY, json)
        editor.apply()
    }

    private fun loadAmmoList() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(AMMO_LIST_KEY, null)
        if (json != null) {
            val type = object : TypeToken<List<BulletData>>() {}.type
            listOfAmmo = Gson().fromJson(json, type)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Load ammo list from SharedPreferences
        loadAmmoList()

        if (firstOpen) {
            firstOpen = false
        }
        contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnNavigationItemSelectedListener(BottomNavigationView.OnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchContent(R.layout.celownik_layout)
                    return@OnNavigationItemSelectedListener true
                }

                R.id.nav_dashboard -> {
                    switchContent(R.layout.ammo_layout)
                    return@OnNavigationItemSelectedListener true
                }
            }
            false
        })

        // Initialize with the home view
        if (savedInstanceState == null) {
            switchContent(R.layout.celownik_layout)
        }
    }

    private fun switchContent(layoutResID: Int) {
        contentFrame!!.removeAllViews()
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val newContent = inflater.inflate(layoutResID, null)
        contentFrame!!.addView(newContent)

        // Initialize specific functionalities depending on the layout
        if (layoutResID == R.layout.celownik_layout) {
            initializeHomeLayout(newContent)
        } else if (layoutResID == R.layout.ammo_layout) {
            initializeAmmoLayout(newContent)
        }
    }

    private fun initializeHomeLayout(layoutView: View) {
        val editTextOdleglosc = layoutView.findViewById<EditText>(R.id.edit_text_odleglosc)
        val editTextWysokosc = layoutView.findViewById<EditText>(R.id.edit_text_wysokosc)
        val editTextZeroRange = layoutView.findViewById<EditText>(R.id.edit_text_zero_range)
        val button = layoutView.findViewById<Button>(R.id.button_calculate)
        val buttonSendData = layoutView.findViewById<Button>(R.id.button_sendData)

        // Initialize the Spinner
        val listaAmunicji = layoutView.findViewById<Spinner>(R.id.lista_amunicji)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOfAmmo.map { it.name })
        listaAmunicji.adapter = adapter
        listaAmunicji.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedAmmo = parent.getItemAtPosition(position).toString()
                pos = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Hide the sight and dot views initially
        val sightView = layoutView.findViewById<View>(R.id.sight)
        val dotView = layoutView.findViewById<View>(R.id.dot)
        sightView.visibility = View.GONE
        dotView.visibility = View.GONE

        // Button click logic
        button.setOnClickListener {
            try {
                val amunicja = selectedAmmo
                val aktBul = listOfAmmo[pos]

                val odlegloscStr = editTextOdleglosc.text.toString()
                val wysokoscStr = editTextWysokosc.text.toString()
                val zeroRangeStr = editTextZeroRange.text.toString()

                if (odlegloscStr.isEmpty() || zeroRangeStr.isEmpty()) {
                    Toast.makeText(this, "Wypełnij wszystkie wymagane pola", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val odleglosc = odlegloscStr.toDouble()
                val wysokosc = if (wysokoscStr.isNotEmpty()) wysokoscStr.toDouble() else null
                val zeroRange = zeroRangeStr.toDouble()

                val nowePolozenieKropki = obliczonka(
                    aktBul.bc, aktBul.fpsMuzzle, aktBul.diameter,
                    aktBul.grain, odleglosc, zeroRange
                )

                // Set the new position of the dot
                dotView.translationY = nowePolozenieKropki.toFloat() * 10

                // Show the sight and dot views
                sightView.visibility = View.VISIBLE
                dotView.visibility = View.VISIBLE

                // Create and show the AlertDialog
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Dane obliczeniowe")
                    .setMessage(
                        "Wartości:\n$amunicja\n$odleglosc\n$wysokosc\n$zeroRange" +
                                "\n$nowePolozenieKropki\nBC: ${aktBul.bc}\nV0: ${aktBul.fpsMuzzle}\n" +
                                "Diameter: ${aktBul.diameter}\nGrain: ${aktBul.grain}"
                    )
                    .setPositiveButton("OK") { dialog, which -> dialog.dismiss() }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "Wystąpił błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        buttonSendData.setOnClickListener {
            try {
                val amunicja = selectedAmmo
                val aktBul = listOfAmmo[pos]
                val odlegloscStr = editTextOdleglosc.text.toString()
                val wysokoscStr = editTextWysokosc.text.toString()
                val zeroRangeStr = editTextZeroRange.text.toString()

                if (odlegloscStr.isEmpty() || zeroRangeStr.isEmpty()) {
                    Toast.makeText(this, "Wypełnij wszystkie wymagane pola", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val odleglosc = odlegloscStr.toDouble()
                val wysokosc = if (wysokoscStr.isNotEmpty()) wysokoscStr.toDouble() else null
                val zeroRange = zeroRangeStr.toDouble()

                val data = mapOf(
                    "grain" to aktBul.grain,
                    "diameter" to aktBul.diameter,
                    "bc" to aktBul.bc,
                    "fpsMuzzle" to aktBul.fpsMuzzle,
                    "odleglosc" to odleglosc,
                    "wysokosc" to wysokosc,
                    "zeroRange" to zeroRange
                )
                val jsonData = Gson().toJson(data)
                sendDataToESP32(jsonData)
            } catch (e: Exception) {
                Toast.makeText(this, "Wystąpił błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeAmmoLayout(layoutView: View) {
        val buttonImportFile: Button = layoutView.findViewById(R.id.importButton)
        buttonImportFile.setOnClickListener {
            try {
                checkPermissionsAndOpenFilePicker()
            } catch (e: Exception) {
                Toast.makeText(this, "Wystąpił błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        val buttonExportFile: Button = layoutView.findViewById(R.id.exportButton)
        buttonExportFile.setOnClickListener {
            try {
                showAmmoExportDialog()
            } catch (e: Exception) {
                Toast.makeText(this, "Wystąpił błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        val buttonClearAmmo: Button = layoutView.findViewById(R.id.clearAmmoButton)
        buttonClearAmmo.setOnClickListener {
            try {
                showAmmoDeletionDialog()
            } catch (e: Exception) {
                Toast.makeText(this, "Wystąpił błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        val userFileName = layoutView.findViewById<EditText>(R.id.userFilename)
        val userBC = layoutView.findViewById<EditText>(R.id.userBC)
        val userDiameter = layoutView.findViewById<EditText>(R.id.userDiameter)
        val userGrain = layoutView.findViewById<EditText>(R.id.userGrain)
        val userV0 = layoutView.findViewById<EditText>(R.id.userV0)
        val userAmmoAdd = layoutView.findViewById<Button>(R.id.userAmmoAdd)

        userAmmoAdd.setOnClickListener {
            try {
                val file = userFileName.text.toString()
                val user_grain = userGrain.text.toString()
                val user_diameter = userDiameter.text.toString()
                val user_bc = userBC.text.toString()
                val user_fpsMuzzle = userV0.text.toString()

                arraylist.add(file.removeSuffix(".txt"))
                listOfAmmo.add(BulletData(file, user_grain.toDouble(), user_diameter.toDouble(), user_bc.toDouble(), user_fpsMuzzle.toDouble()))
                val newAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOfAmmo.map { it.name })
                // Update the Spinner adapter if necessary
                // listaAmunicji.adapter = newAdapter
                saveAmmoList()

                Toast.makeText(applicationContext, "Dodano amunicję", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Wystąpił błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun showAmmoExportDialog() {
        val ammoNames = listOfAmmo.map { it.name }.toTypedArray()
        val selectedItems = booleanArrayOf(*BooleanArray(ammoNames.size))

        AlertDialog.Builder(this)
            .setTitle("Wybierz amunicję do eksportu")
            .setMultiChoiceItems(ammoNames, selectedItems) { dialog, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton("Eksportuj") { dialog, which ->
                val itemsToExport = mutableListOf<BulletData>()
                selectedItems.forEachIndexed { index, isSelected ->
                    if (isSelected) {
                        itemsToExport.add(listOfAmmo[index])
                    }
                }
                createFileInDocuments(itemsToExport)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun createFileInDocuments(itemsToExport: List<BulletData>) {
        this.itemsToExport = itemsToExport
        createFileLauncher.launch("exported_ammo.json")
    }

    private fun saveObjectListToJsonUri(uri: Uri) {
        val gson = Gson()
        val jsonString = gson.toJson(itemsToExport ?: emptyList<BulletData>())
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(jsonString.toByteArray())
        }
    }

    private fun showAmmoDeletionDialog() {
        val ammoNames = listOfAmmo.map { it.name }.toTypedArray()
        val selectedItems = booleanArrayOf(*BooleanArray(ammoNames.size))

        AlertDialog.Builder(this)
            .setTitle("Wybierz amunicję do usunięcia")
            .setMultiChoiceItems(ammoNames, selectedItems) { dialog, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton("Usuń") { dialog, which ->
                val itemsToDelete = mutableListOf<BulletData>()
                selectedItems.forEachIndexed { index, isSelected ->
                    if (isSelected) {
                        itemsToDelete.add(listOfAmmo[index])
                    }
                }
                listOfAmmo.removeAll(itemsToDelete)
                updateAmmoSpinner()
                // Save the updated ammo list
                saveAmmoList()
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun updateAmmoSpinner() {
        val newAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOfAmmo.map { it.name })
        newAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private val gson = Gson()

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readJsonDataFromUri(it) }
    }

    private fun checkPermissionsAndOpenFilePicker() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    1001
                )
            }
            else -> {
                openFilePicker()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Uprawnienia zostały nadane, możemy otworzyć selektor plików
                    openFilePicker()
                } else {
                    // Uprawnienia nie zostały nadane, możesz obsłużyć ten przypadek tutaj
                }
                return
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun openFilePicker() {
        val mimeTypes = arrayOf("application/json")
        openFileLauncher.launch(mimeTypes)
    }

    private fun readJsonDataFromUri(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        inputStream?.use {
            val jsonString = it.reader().readText()
            val listType = object : TypeToken<List<BulletData>>() {}.type
            val dataList = gson.fromJson<List<BulletData>>(jsonString, listType)
            updateUIWithLoadedData(dataList)
        }
    }

    private fun updateUIWithLoadedData(dataList: List<BulletData>) {
        listOfAmmo.addAll(dataList)
        val newAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOfAmmo.map { it.name })
        // Update the Spinner adapter if necessary
        //listaAmunicji.adapter = newAdapter
    }

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            saveObjectListToJsonUri(it)
        }
    }

    private fun sendDataToESP32(data: String) {
        val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = RequestBody.create(JSON, data)
        val request = Request.Builder()
            .url("http://192.168.0.15/data")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "problemyy", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Data sent successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to send data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}

fun obliczonka(
    dragCoefficient: Double,
    v0: Double,
    dia: Double,
    mass: Double,
    distance: Double,
    zerorange: Double
): Double {
    // Bullet mass in kg converted from grains
    val bullet_mass = mass * 0.0000648
    // Bullet diameter from inch to meters
    val bullet_dia = dia * 0.0254
    // Bullet cross area
    val bullet_area = bullet_dia.pow(2) * 3.14 / 4
    // Velocity from fps to m/s
    val velocity = v0 * 0.3048
    val air_density = 1.225 // Przyjęta gęstość powietrza w kg/m^3 (dla warunków standardowych)
    val effective_distance = distance
    // Obliczenie opadu pocisku w MOA
    val drop_adjustment = calculateDropAdjustment(
        dragCoefficient,
        velocity,
        bullet_mass,
        bullet_area,
        effective_distance,
        air_density
    )

    // return moa
    return round((drop_adjustment / distance) / 100000 * 3.44)
}

fun calculateDropAdjustment(
    dragCoefficient: Double,
    v0: Double,
    bulletMass: Double,
    bulletArea: Double,
    distance: Double,
    airDensity: Double
): Double {
    return -0.5 * airDensity * dragCoefficient * bulletArea * v0.pow(2) * distance.pow(2) / bulletMass
}
