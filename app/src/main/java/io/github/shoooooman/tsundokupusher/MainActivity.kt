package io.github.shoooooman.tsundokupusher

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val bookList: MutableList<Book> = mutableListOf()
    private val tag = "main"

    private fun calcSumPages() : Int {
        var sum = 0
        for (book in bookList) {
            sum += book.pages
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
            val diff = if (tmp == 0L) 1 else tmp
            val pages = book.pages - book.readPages
            sum += pages/diff
        }

        return sum.toLong()
    }

    private fun setUpdatePageButtonListener() {
        val spinner: Spinner = findViewById(R.id.spinner)
        val selectedPages: EditText = findViewById(R.id.selectedPages)
        val updatePageButton: Button = findViewById(R.id.updatePageButton)
        val todayPages: TextView = findViewById(R.id.todayPages)
        val alreadyReadPages: TextView = findViewById(R.id.alreadyReadPages)

        updatePageButton.setOnClickListener {
            val book = spinner.selectedItem as Book
            val readPages = Integer.parseInt(selectedPages.text.toString())
            book.readPages += readPages
            alreadyReadPages.text = getString(R.string.already_read_pages, book.readPages)

            val today = Integer.parseInt(todayPages.text.toString())
            todayPages.text = (today - readPages).toString()

            // 読み終わり
            if (book.pages <= book.readPages) {
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

    private fun setAddButtonListener() {
        val editName: EditText = findViewById(R.id.editName)
        val editPages: EditText = findViewById(R.id.editPages)
        val editDeadline: EditText = findViewById(R.id.editDeadline)
        val addButton: Button = findViewById(R.id.addButton)
        val todayPages: TextView = findViewById(R.id.todayPages)
        val spinner: Spinner = findViewById(R.id.spinner)

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
            todayPages.text = today.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSpinner()
        setAddButtonListener()
        setUpdatePageButtonListener()
    }
}
