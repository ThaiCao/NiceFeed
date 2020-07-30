package com.joshuacerdenia.android.nicefeed.ui.dialog

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.joshuacerdenia.android.nicefeed.R

private const val ARG_COUNT = "ARG_COUNT"
private const val ARG_TITLE = "ARG_TITLE"
private const val ARG_CATEGORIES = "ARG_CATEGORIES"

class EditCategoryFragment : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(categories: Array<String>,
                        title: String?,
                        count: Int = 1
        ): EditCategoryFragment {
            val args = Bundle().apply {
                putInt(ARG_COUNT, count)
                putString(ARG_TITLE, title)
                putStringArray(ARG_CATEGORIES, categories)
            }
            return EditCategoryFragment()
                .apply {
                arguments = args
            }
        }
    }

    private lateinit var dialogMessage: TextView
    private lateinit var categoryTextView: AutoCompleteTextView
    private lateinit var cancelButton: Button
    private lateinit var confirmButton: Button

    interface Callbacks {
        fun onEditCategoryConfirmed(category: String)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogNoFloating)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit_category, container, false)
        dialogMessage = view.findViewById(R.id.dialog_message)
        categoryTextView = view.findViewById(R.id.category_edit_text)
        cancelButton = view.findViewById(R.id.cancel_button)
        confirmButton = view.findViewById(R.id.confirm_button)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val count = arguments?.getInt(ARG_COUNT) ?: 1
        val title = arguments?.getString(ARG_TITLE)
        val categories = arguments?.getStringArray(ARG_CATEGORIES)?.toList() ?: emptyList()
        val adapter = context?.let {
            ArrayAdapter(it, android.R.layout.simple_list_item_1, categories)
        }

        val whatToEdit = title ?: resources.getQuantityString(R.plurals.numberOfFeeds, count, count)
        dialogMessage.text = getString(R.string.edit_category_dialog_message, whatToEdit)

        categoryTextView.apply {
            setAdapter(adapter)
            this.threshold = 1

            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    // Left blank on purpose
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // This one too
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    confirmButton.isEnabled = s?.length in 1..50
                }
            })
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        confirmButton.apply {
            isEnabled = false

            setOnClickListener {
                val category = categoryTextView.text.toString().trim()
                targetFragment?.let { (it as Callbacks).onEditCategoryConfirmed(category) }
                dismiss()
            }
        }
    }
}