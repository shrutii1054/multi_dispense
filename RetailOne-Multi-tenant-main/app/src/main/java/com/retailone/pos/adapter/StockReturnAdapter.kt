import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.retailone.pos.R
import com.retailone.pos.databinding.ItemStockReturnCardBinding
import com.retailone.pos.utils.LocalizationUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StockReturnAdapter(
    private val list: List<StockReturn>,
    private val onDispatchClicked: (StockReturn) -> Unit,
    private val onItemClicked: (StockReturn) -> Unit

) : RecyclerView.Adapter<StockReturnAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemStockReturnCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemStockReturnCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val context = holder.itemView.context
        val productCount = item.products.size

        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        var date: Date? = null
        for (format in formats) {
            try {
                date = SimpleDateFormat(format, Locale.getDefault()).parse(item.requested_date)
                if (date != null) break
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        val displayDate = date ?: Date()
        val day = SimpleDateFormat("dd", Locale.getDefault()).format(displayDate)
        val calendar = Calendar.getInstance().apply { time = displayDate }
        val month = LocalizationUtils.getLocalizedMonthShort(context, calendar.get(Calendar.MONTH)).uppercase()

        with(holder.binding) {
            tvDay.text = day
            tvMonth.text = month
            tvReturnId.text = context.getString(R.string.id_hash_label, item.id.toString())
            tvItemCount.text = "$productCount item${if (productCount > 1) "s" else ""}"

            if (item.status == 2) {
                tvStatus.text = context.getString(R.string.status_return_approved)
                tvStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                btnDispatch.visibility = View.VISIBLE
            } else if (item.status == 3) {
            tvStatus.text = context.getString(R.string.status_rejected)
            tvStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            btnDispatch.visibility = View.GONE
        }else if (item.status == 4 ) {
                tvStatus.text = context.getString(R.string.status_dispatched)
                tvStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
                btnDispatch.visibility = View.GONE
            }else if (item.status == 5) {
                tvStatus.text = context.getString(R.string.status_received)
                tvStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_purple))
                btnDispatch.visibility = View.GONE
            }
        else {
                tvStatus.text = context.getString(R.string.status_pending)
                tvStatus.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                btnDispatch.visibility = View.GONE
            }

            btnDispatch.setOnClickListener { onDispatchClicked(item) }
            // ✅ NEW - handle item click to trigger onItemClicked lambda
            itemview.setOnClickListener { onItemClicked(item) }
        }
    }

    override fun getItemCount() = list.size
}
