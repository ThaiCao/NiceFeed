package com.joshuacerdenia.android.nicefeed.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModelProvider
import com.joshuacerdenia.android.nicefeed.R
import com.joshuacerdenia.android.nicefeed.data.local.NiceFeedPreferences
import com.joshuacerdenia.android.nicefeed.databinding.FragmentEntryBinding
import com.joshuacerdenia.android.nicefeed.databinding.ToolbarBinding
import com.joshuacerdenia.android.nicefeed.ui.OnToolbarInflated
import com.joshuacerdenia.android.nicefeed.ui.dialog.TextSizeFragment
import com.joshuacerdenia.android.nicefeed.ui.viewmodel.EntryViewModel
import com.joshuacerdenia.android.nicefeed.util.Utils
import com.joshuacerdenia.android.nicefeed.util.extensions.hide
import com.joshuacerdenia.android.nicefeed.util.extensions.shortened
import com.joshuacerdenia.android.nicefeed.util.extensions.show
import com.squareup.picasso.Picasso
import java.text.DateFormat
import java.util.*

class EntryFragment: VisibleFragment(), TextSizeFragment.Callbacks {

    interface Callbacks: OnToolbarInflated

    private var _binding : FragmentEntryBinding? = null
    private val binding get() = _binding!!

    private var _toolbarBinding: ToolbarBinding? = null
    private val toolbarBinding get() = _toolbarBinding!!

    private lateinit var viewModel: EntryViewModel
    private  var themeApp = 0

    private var callbacks: Callbacks? = null
    private var starItem: MenuItem? = null
    private var textSizeItem: MenuItem? = null
    private val fragment = this@EntryFragment


    override fun onAttach(context: Context) {
        super.onAttach(context)
        callbacks = context as Callbacks?
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(EntryViewModel::class.java)
        viewModel.apply {
            setTextSize(NiceFeedPreferences.getTextSize(requireContext()))
            font = NiceFeedPreferences.getFont(requireContext())
            bannerIsEnabled = NiceFeedPreferences.bannerIsEnabled(requireContext())
        }

        arguments?.getString(ARG_ENTRY_ID)?.let { entryId -> viewModel.getEntryById(entryId) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEntryBinding.inflate(inflater, container, false)
        _toolbarBinding = binding.toolbar

        themeApp = AppCompatDelegate.getDefaultNightMode()

        binding.webView.apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                builtInZoomControls = false
                displayZoomControls = false
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // Open all links with default browser.
                    request?.url?.let { url -> Utils.openLink(requireActivity(), binding.root, url) }
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (themeApp == AppCompatDelegate.MODE_NIGHT_YES && Build.VERSION.SDK_INT<29) {
                        val code = """javascript:(function() {
                        var node = document.createElement('style');
                        node.type = 'text/css';
                        var link = document.links;
                        l = link.length;
                        for(i=0;i<l;i++){
                            link[i].style.color = '#6666ff';
                        }
                        node.innerHTML = 'body {
                            color: white;
                            background-color: transparent;
                        }';
                        document.head.appendChild(node);
                     
                    })()""".trimIndent()

                        loadUrl(code)
                    }

                    if (!viewModel.isInitialLoading) {
                        val position = viewModel.lastPosition
                        binding.nestedScrollView.smoothScrollTo(position.first, position.second)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    binding.progressBar.progress = newProgress
                    if (newProgress == 100) binding.progressBar.hide()
                }
            }
        }

        toolbarBinding.toolbar.title = getString(R.string.loading)
        callbacks?.onToolbarInflated(toolbarBinding.toolbar)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.htmlLiveData.observe(viewLifecycleOwner, { data ->
            if (data != null) {
                binding.webView.loadData(data, MIME_TYPE, ENCODING)
                toggleBannerViews(viewModel.bannerIsEnabled)
                setHasOptionsMenu(true)
                toolbarBinding.toolbar.title = viewModel.entry?.website?.shortened()
                if (viewModel.bannerIsEnabled) viewModel.entry?.let { entry ->
                    updateBanner(entry.title, entry.date, entry.author)
                    Picasso.get().load(entry.image).fit().centerCrop()
                        .placeholder(R.drawable.vintage_newspaper).into(binding.imageView)
                }
            } else {
                toggleBannerViews(false)
                binding.progressBar.hide()
                toolbarBinding.toolbar.title = getString(R.string.app_name)
                Utils.showErrorMessage(binding.root, resources)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        toolbarBinding.toolbar.setOnClickListener { binding.nestedScrollView.smoothScrollTo(0, 0) }
        binding.imageView.setOnClickListener { handleViewInBrowser() }
    }

    private fun toggleBannerViews(isEnabled: Boolean) {
        if (isEnabled) {
            binding.imageView.show()
            binding.titleTextView.show()
            binding.subtitleTextView.show()
        } else {
            binding.imageView.hide()
            binding.titleTextView.hide()
            binding.subtitleTextView.hide()
        }
    }

    private fun updateBanner(title: String, date: Date?, author: String?) {
        binding.titleTextView.text = HtmlCompat.fromHtml(title, 0)
        val formattedDate = date?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
        }
        binding.subtitleTextView.text = when {
            author.isNullOrEmpty() -> formattedDate
            formattedDate.isNullOrEmpty() -> author
            else -> "$formattedDate – $author"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_entry, menu)
        starItem = menu.findItem(R.id.item_star)
        textSizeItem = menu.findItem(R.id.item_text_size)
        viewModel.entry?.let { entry -> toggleStarOptionItem(entry.isStarred) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.item_star -> handleStar()
            R.id.item_share -> handleShare()
            R.id.item_copy_link -> handleCopyLink()
            R.id.item_view_in_browser -> handleViewInBrowser()
            R.id.item_text_size -> handleChangeTextSize()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleStar(): Boolean {
        viewModel.entry?.let { entry ->
            entry.isStarred = !entry.isStarred
            toggleStarOptionItem(entry.isStarred)
            return true
        } ?: return false
    }

    private fun toggleStarOptionItem(isStarred: Boolean) {
        starItem?.apply {
            title = if (isStarred) {
                setIcon(R.drawable.ic_star_yellow)
                getString(R.string.unstar)
            } else {
                setIcon(R.drawable.ic_star_border)
                getString(R.string.star)
            }
        }
    }

    private fun handleShare(): Boolean {
        viewModel.entry?.let { entry ->
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, entry.title)
                putExtra(Intent.EXTRA_TEXT, entry.url)
            }.also { intent ->
                val chooserIntent = Intent.createChooser(intent, getString(R.string.share_entry))
                startActivity(chooserIntent)
            }
            return true
        } ?: return false
    }

    private fun handleCopyLink(): Boolean {
        viewModel.entry?.let { entry ->
            Utils.copyLinkToClipboard(requireContext(), entry.url, binding.root)
            return true
        } ?: return false
    }

    private fun handleViewInBrowser(): Boolean {
        Utils.openLink(requireActivity(), binding.root, Uri.parse(viewModel.entry?.url))
        return true
    }

    private fun handleChangeTextSize(): Boolean {
        saveScrollPosition()
        TextSizeFragment.newInstance(viewModel.textSize).apply {
            setTargetFragment(fragment, 0)
            show(fragment.parentFragmentManager, "change text size")
        }
        return true
    }

    override fun onTextSizeSelected(textSize: Int) {
        viewModel.setTextSize(textSize)
    }

    private fun saveScrollPosition() {
        viewModel.lastPosition = Pair(
            binding.nestedScrollView.scrollX,
            binding.nestedScrollView.scrollY
        )
    }

    override fun onStop() {
        super.onStop()
        saveScrollPosition()
        viewModel.isInitialLoading = false
        viewModel.saveChanges()
        context?.let { NiceFeedPreferences.saveTextSize(it, viewModel.textSize) }
    }

    override fun onDetach() {
        super.onDetach()
        callbacks = null
    }

    companion object {
        private const val ARG_ENTRY_ID = "ARG_ENTRY_ID"
        private const val MIME_TYPE = "text/html; charset=UTF-8"
        private const val ENCODING = "base64"

        fun newInstance(entryId: String): EntryFragment {
            return EntryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ENTRY_ID, entryId)
                }
            }
        }
    }
}