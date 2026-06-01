package com.retailone.pos.adapter



import ReturnedProduct
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.retailone.pos.R
import com.retailone.pos.databinding.ItemProductBottomsheetBinding
import com.retailone.pos.models.GoodsToWarehouseModel.ReturnStocks.ProductModel

class ProductReturnAlertAdapter(
    private val products: List<ReturnedProduct>
) : RecyclerView.Adapter<ProductReturnAlertAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemProductBottomsheetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ReturnedProduct) {
            binding.tvProductName.text = product.product?.product_name ?: "Unknown Product"
            binding.tvQuantity.text = binding.root.context.getString(R.string.qty_colon) + product.quantity
            binding.tvCondition.text = binding.root.context.getString(R.string.condition_colon) +
                com.retailone.pos.utils.LocalizationUtils.getLocalizedStatus(binding.root.context, product.condition)
            binding.tvApprovedQuantity.text = binding.root.context.getString(R.string.approved_qty_colon) + product.approved_quantity
            binding.tvReceivedQuantity.text = binding.root.context.getString(R.string.received_qty_colon) + product.received_quantity
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductBottomsheetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount() = products.size
}
