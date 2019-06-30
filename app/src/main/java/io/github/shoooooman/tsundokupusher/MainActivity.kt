package io.github.shoooooman.tsundokupusher

import android.content.Intent
import android.graphics.BitmapFactory
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.google.zxing.integration.android.IntentIntegrator
import com.robinhood.ticker.TickerUtils
import com.robinhood.ticker.TickerView
import com.squareup.moshi.Moshi
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val bookList: MutableList<Book> = mutableListOf()

    private fun calcSumRestPages() : Int {
        var sum = 0
        for (book in bookList) {
            sum += (book.pages - book.readPages)
        }

        return sum
    }

    private fun setSpinner() {
        val spinner: Spinner = findViewById(R.id.spinner)

        val adapter = ArrayAdapter(applicationContext,
            android.R.layout.simple_spinner_item, bookList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val spinnerParent = parent as Spinner
                val selectedBook = spinnerParent.selectedItem as Book
                val alreadyReadPages: TextView = findViewById(R.id.alreadyReadPages)
                val sumPages:TextView = findViewById(R.id.selectedSumPages)
                val selectedDeadline: EditText = findViewById(R.id.selectedDeadline)

                alreadyReadPages.text = getString(R.string.already_read_pages, selectedBook.readPages)
                sumPages.text = getString(R.string.sum_of_pages, selectedBook.pages)

                val df = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)
                selectedDeadline.setText(df.format(selectedBook.deadline))
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun calcTodayPages() : Long {
        var sum = 0.0
        val today = Date().time
        for (book in bookList) {
            val deadline = book.deadline.time
            val tmp = (deadline - today) / (1000*60*60*24)
            // 期限まで1日以下ならdiffを1日とする
            val diff = if (tmp == 0L) 1 else tmp
            val pages = book.pages - book.readPages
            sum += pages/diff
        }

        return sum.toLong()
    }

    private fun setUpdateTodayPagesButtonListener() {
        val updateTodayPagesButton: ImageButton = findViewById(R.id.updateTodayPages)
        val tickerView: TickerView = findViewById(R.id.todayPages)

        updateTodayPagesButton.setOnClickListener {
            val pages = calcTodayPages()
            tickerView.text = pages.toString()
        }
    }

    private fun setUpdatePageButtonListener() {
        val spinner: Spinner = findViewById(R.id.spinner)
        val selectedPages: EditText = findViewById(R.id.selectedPages)
        val updatePageButton: Button = findViewById(R.id.updatePageButton)
        val tickerView: TickerView = findViewById(R.id.todayPages)
        val allPages: TextView = findViewById(R.id.sumAllRestPages)
        val alreadyReadPages: TextView = findViewById(R.id.alreadyReadPages)

        updatePageButton.setOnClickListener {
            val book = spinner.selectedItem as Book
            val readPages = Integer.parseInt(selectedPages.text.toString())
            book.readPages += readPages
            alreadyReadPages.text = getString(R.string.already_read_pages, book.readPages)

            val today = Integer.parseInt(tickerView.text.toString())
            tickerView.text = (today - readPages).toString()

            val rest = calcSumRestPages()
            allPages.text = getString(R.string.sum_of_pages, rest)

            // 読み終わり
            if (book.pages <= book.readPages) {
                // トーストを表示
                val message = "読破おめでとうございます！"
                val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()

                // bookListから読み終わった本を削除
                bookList.removeAll { it.name == book.name}
                @Suppress("UNCHECKED_CAST")
                val adapter = spinner.adapter as ArrayAdapter<String>
                adapter.notifyDataSetChanged()

                // bookListが空なら状態を初期化
                if (bookList.isEmpty()) {
                    alreadyReadPages.text = getString(R.string.already_read_pages_default)
                    val sumPages: TextView = findViewById(R.id.selectedSumPages)
                    sumPages.text = getString(R.string.sum_of_pages_default)
                    val deadline: EditText = findViewById(R.id.selectedDeadline)
                    deadline.text.clear()
                }
            }

            selectedPages.text.clear()
        }

    }

    private fun setScanButtonListener() {
        val scanButton: ImageButton = findViewById(R.id.scanButton)

        scanButton.setOnClickListener {
            val integrator = IntentIntegrator(this)
            // set orientation vertical
            integrator.captureActivity = AnyOrientationCaptureActivity::class.java
            integrator.setOrientationLocked(true)
            integrator.setBeepEnabled(false)
            integrator.initiateScan()
        }

    }

    private fun parseJson(jsonString: String) : Triple<String?, Int?, String?> {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(BookInfo::class.java)
        val bookInfo = adapter.fromJson(jsonString)

        return if (bookInfo != null) {
            val items = bookInfo.items
            val bookInfoDetail = items[0]
            val volumeInfo = bookInfoDetail.volumeInfo
            Log.d("volumeInfo", volumeInfo.toString())
            Triple(volumeInfo.title, volumeInfo.pageCount, volumeInfo.imageLinks.smallThumbnail)
        } else {
            Triple(null, null, null)
        }
    }

    private fun getThumbnail(url : String) {
        url.httpGet().response { _, _, result ->
            when (result) {
                is Result.Failure -> {
                    Log.d("thumbnail", "取得失敗")
                }
                is Result.Success -> {
                    val bytes = result.value
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    // UIの変更はUIスレッドで行う
                    runOnUiThread {
                        if (bitmap != null) {
                            val bookImage: ImageView = findViewById(R.id.bookImage)
                            bookImage.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }
    }

    private fun getBookInfo(isbn : String) {
        // google book api
        val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn"

        // responseを受け取る部分はfuelが自動で別スレッドで実行してくれている
        url.httpGet().responseString { _, response, result ->
            when (result) {
                is Result.Failure -> {
                    Log.d("main", "通信に失敗")
                }
                is Result.Success -> {
                    // with header
                    Log.d("main", response.toString())
                    // body
                    Log.d("result", result.value)

                    val (title, pageCount, thumbnailURL) = parseJson(result.value)
                    Log.d("data", title)
                    Log.d("data", pageCount.toString())
                    Log.d("data", thumbnailURL)

                    // UIの変更はUIスレッドで行う
                    runOnUiThread {
                        if (title != null) {
                            val bookName: EditText = findViewById(R.id.editName)
                            bookName.setText(title, TextView.BufferType.EDITABLE)
                        }
                        if (pageCount != null) {
                            val bookPages: EditText = findViewById(R.id.editPages)
                            bookPages.setText(pageCount.toString(), TextView.BufferType.EDITABLE)
                        }
                    }

                    // サムネイルを取得しImageViewにセット
                    if (thumbnailURL != null) {
                        getThumbnail(thumbnailURL)
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d("result", requestCode.toString())
        Log.d("result", data.toString())
        if (data != null) {
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            // バーコードからISBNを取得
            if (result != null) {
                // ISBNコード
                Log.d("main", result.contents)

                getBookInfo(result.contents)
            }
        }
    }

    private fun setAddButtonListener() {
        val editName: EditText = findViewById(R.id.editName)
        val editPages: EditText = findViewById(R.id.editPages)
        val editDeadline: EditText = findViewById(R.id.editDeadline)
        val addButton: Button = findViewById(R.id.addButton)
        val ticker: TickerView = findViewById(R.id.todayPages)
        val sumAllRestPages: TextView = findViewById(R.id.sumAllRestPages)
        val spinner: Spinner = findViewById(R.id.spinner)

        // setting of ticker
        ticker.setCharacterLists(TickerUtils.provideNumberList())
        ticker.animationDuration = 500

        addButton.setOnClickListener {
            val name = editName.text.toString()
            val pages = Integer.parseInt(editPages.text.toString())
            val format = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)
            val deadline = format.parse(editDeadline.text.toString())
            val book = Book(name, pages, deadline, 0)
            bookList.add(book)

            @Suppress("UNCHECKED_CAST")
            val adapter = spinner.adapter as ArrayAdapter<String>
            adapter.notifyDataSetChanged()

            val today = calcTodayPages()
            ticker.text = today.toString()

            val rest = calcSumRestPages()
            sumAllRestPages.text = getString(R.string.sum_of_pages, rest)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setUpdateTodayPagesButtonListener()
        setSpinner()
        setScanButtonListener()
        setAddButtonListener()
        setUpdatePageButtonListener()
    }

}
