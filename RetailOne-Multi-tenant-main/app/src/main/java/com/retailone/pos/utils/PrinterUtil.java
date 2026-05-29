package com.retailone.pos.utils;


import static android.provider.MediaStore.Images.Media.getBitmap;

import android.app.AlertDialog;
import android.app.ProgressDialog;

import com.retailone.pos.models.PointofsaleModel.PosSaleModel.PosSalesItem;
import com.retailone.pos.models.PosSalesDetailsModel.CopyReceiptRes;
import com.retailone.pos.models.PosSalesDetailsModel.ReceiptType;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.common.apiutil.CommonException;
import com.common.apiutil.printer.NewUsbThermalPrinter;
import com.common.apiutil.printer.UsbThermalPrinter;
//import com.common.apiutil.util.SDKUtil;
import com.common.apiutil.util.StringUtil;
import com.common.apiutil.util.SystemUtil;
import com.retailone.pos.R;
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper;
import com.retailone.pos.models.LocalizationModel.LocalizationData;
import com.retailone.pos.models.PosSalesDetailsModel.PosSalesDetails;
import com.retailone.pos.models.PosSalesDetailsModel.SalesItem;
import com.retailone.pos.models.PosSalesDetailsModel.TaxSummary;
import com.retailone.pos.models.PrinterModel.ReceiptData;
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes;
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnedItem;
import com.retailone.pos.models.PosSalesDetailsModel.SaleReceiptRes;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import org.jetbrains.annotations.NotNull;

import com.retailone.pos.models.PosSalesDetailsModel.VsdcReceipt;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;



public class PrinterUtil {

    private String printVersion;
    private final int NOPAPER = 3;
    private final int LOWBATTERY = 4;
    private final int PRINTVERSION = 5;
    private final int PRINTBARCODE = 6;
    private final int PRINTQRCODE = 7;
    private final int PRINTPAPERWALK = 8;
    private final int PRINTCONTENT = 9;
    private final int CANCELPROMPT = 10;
    private final int PRINTERR = 11;
    private final int OVERHEAT = 12;
    private final int MAKER = 13;
    private final int PRINTPICTURE = 14;
    private final int NOBLACKBLOCK = 15;
    private final int PRINTSHORTCONTENT = 16;
    private final int PRINTLONGPICTURE = 17;
    private final int PRINTLONGTEXT = 18;
    private final int PRINTBLACK = 19;
    private final int PRINTCOLUMNS = 20;

    private final int TSIZE20 = 36;
    private final int TSIZE22 = 34;
    private final int TSIZE24 = 31;
    private final int TSIZE26 = 29;
    private final int TSIZE28 = 27;
    private final int TSIZE30 = 26;
    private final int TSIZE32 = 23;


    private NewUsbThermalPrinter mUsbThermalPrinter;
    private ProgressDialog dialog;
    private ProgressDialog progressDialog;
    private MyHandler handler;
    private boolean LowBattery = false;
    private Context context;
    private int deviceType;
    private boolean nopaper = false;
    private boolean looseOil = false;
    ArrayList<MyItem> itemx ;

    private String Result;

    private String currency;
    private String zone;
    String printType = "";
    LocalizationData localizationData;

    String productname = "";

    int numberOfItems = 0;

    /** Resolves receipt label using the app-selected locale (en / pt / sw). */
    private String receiptStr(int resId) {
        return LanguageManager.INSTANCE.wrapContext(context).getString(resId);
    }

    private String receiptStr(int resId, Object... formatArgs) {
        return LanguageManager.INSTANCE.wrapContext(context).getString(resId, formatArgs);
    }

    /** Receipt date with localized month abbreviation (e.g. Mai/May/Mei). */
    private String formatReceiptDate(Date date) {
        if (date == null) return "";
        return LocalizationUtils.INSTANCE.formatReceiptDisplayDate(
                LanguageManager.INSTANCE.wrapContext(context), date);
    }

    PosSalesDetails posSalesDetails;
    ReturnSaleRes returnSaleRes;
    SaleReceiptRes copySaleReceiptRes;
    ReceiptData receipt_data;

    public PrinterUtil(Context context) {
        Log.e("PrinterUtil_Debug", "CONSTRUCTOR: PrinterUtil(Context) started!");
        try {
            this.context = context;
            Log.e("PrinterUtil_Debug", "CONSTRUCTOR: Context assigned. Instantiating NewUsbThermalPrinter...");
            mUsbThermalPrinter = new NewUsbThermalPrinter(context);
            Log.e("PrinterUtil_Debug", "CONSTRUCTOR: NewUsbThermalPrinter instantiated. Fetching device type...");
            deviceType = SystemUtil.getDeviceType();
            Log.e("PrinterUtil_Debug", "CONSTRUCTOR: Device type fetched: " + deviceType + ". Instantiating handler...");
            handler = new MyHandler();
            Log.e("PrinterUtil_Debug", "CONSTRUCTOR: Handler instantiated. Fetching currency and timezone...");
            currency =  new LocalizationHelper(context).getLocalizationData().getCurrency();
            zone =  new LocalizationHelper(context).getLocalizationData().getTimezone();
            Log.e("PrinterUtil_Debug", "CONSTRUCTOR: Currency: " + currency + ", Timezone: " + zone + ". Calling initializePrinter...");
            com.retailone.pos.utils.LanguageManager.INSTANCE.logLocaleDebug(context, "PrinterUtil");
            initializePrinter();
            Log.e("PrinterUtil_Debug", "CONSTRUCTOR: PrinterUtil(Context) completed successfully!");
        } catch (Throwable t) {
            Log.e("PrinterUtil_Debug", "CONSTRUCTOR CRASH: Exception/Error caught inside PrinterUtil constructor!", t);
        }
    }

    private void initializePrinter() {
        Log.e("PrinterUtil_Debug", "initializePrinter() called");
        try {
            dialog = new ProgressDialog(context);
            dialog.setTitle(context.getString(R.string.printer_initializing_title));
            dialog.setMessage(context.getString(R.string.please_wait));
            dialog.setCancelable(false);
            try {
                dialog.show();
                Log.e("PrinterUtil_Debug", "initializePrinter(): progress dialog shown");
            } catch (Exception showEx) {
                Log.e("PrinterUtil_Debug", "initializePrinter(): Failed to show dialog (e.g. BadTokenException)", showEx);
            }
        } catch (Exception e) {
            Log.e("PrinterUtil_Debug", "initializePrinter(): Failed to create ProgressDialog", e);
        }

        new Thread(() -> {
            try {
                Log.e("PrinterUtil_Debug", "initializePrinter(): Thread started. starting mUsbThermalPrinter.start(0)...");
                mUsbThermalPrinter.start(0);
                Log.e("PrinterUtil_Debug", "initializePrinter(): mUsbThermalPrinter.start(0) completed successfully");
                Log.e("PrinterUtil_Debug", "initializePrinter(): starting mUsbThermalPrinter.reset()...");
                mUsbThermalPrinter.reset();
                Log.e("PrinterUtil_Debug", "initializePrinter(): mUsbThermalPrinter.reset() completed successfully");
            } catch (CommonException e) {
                Log.e("PrinterUtil_Debug", "initializePrinter(): CommonException occurred during start/reset", e);
                e.printStackTrace();
            } catch (Throwable t) {
                Log.e("PrinterUtil_Debug", "initializePrinter(): Unexpected Throwable in thread", t);
            } finally {
                Log.e("PrinterUtil_Debug", "initializePrinter(): Thread finished. Dismissing dialog if exists...");
                if (dialog != null) {
                    try {
                        dialog.dismiss();
                        Log.e("PrinterUtil_Debug", "initializePrinter(): dialog dismissed");
                    } catch (Exception dismissEx) {
                        Log.e("PrinterUtil_Debug", "initializePrinter(): Failed to dismiss dialog", dismissEx);
                    }
                }
            }
        }).start();
    }

    public void printReceiptData(PosSalesDetails _posSalesDetails) {
        printType = "SALE";
        Log.e("PrinterUtil_Debug", "printReceiptData() called. printType = " + printType);
        if (_posSalesDetails == null) {
            Log.e("PrinterUtil_Debug", "printReceiptData(): _posSalesDetails is NULL!");
        } else {
            Log.e("PrinterUtil_Debug", "printReceiptData(): invoice_id = " + 
                (_posSalesDetails.getData() != null ? _posSalesDetails.getData().getInvoice_id() : "null data"));
        }

        //  receipt_data = receiptData;
        posSalesDetails = _posSalesDetails;

        Log.e("PrinterUtil_Debug", "printReceiptData(): LowBattery = " + LowBattery + ", nopaper = " + nopaper);

        if (LowBattery) {
            Log.e("PrinterUtil_Debug", "printReceiptData(): LowBattery is true! Sending LOWBATTERY message");
            handler.sendMessage(handler.obtainMessage(LOWBATTERY, 1, 0, null));
        } else {
            if (!nopaper) {
                // handler.sendMessage(handler.obtainMessage(PRINTPICTURE, 1, 0, null));

                Log.e("PrinterUtil_Debug", "printReceiptData(): Paper is available. Sending PRINTCONTENT message");
                handler.sendMessage(handler.obtainMessage(PRINTCONTENT, 1, 0, null));
                //Toast.makeText(context, "Paper Available", Toast.LENGTH_LONG).show();

            } else {
                Log.e("PrinterUtil_Debug", "printReceiptData(): No paper detected!");
                Toast.makeText(context, context.getString(R.string.toast_no_paper_detected), Toast.LENGTH_LONG).show();
            }
        }

    }
    private void logReceiptLine(String line) {
        Log.e("FULL_RECEIPT_LOG", line);
    }


    public void printReturnReceiptData( ReturnSaleRes _returnSaleRes) {
        printType = "RETURN";
        Log.e("PrinterUtil_Debug", "printReturnReceiptData() called. printType = " + printType);
        if (_returnSaleRes == null) {
            Log.e("PrinterUtil_Debug", "printReturnReceiptData(): _returnSaleRes is NULL!");
        } else {
            Log.e("PrinterUtil_Debug", "printReturnReceiptData(): returned_invoice_id = " + 
                (_returnSaleRes.getData() != null ? _returnSaleRes.getData().getReturned_invoice_id() : "null data"));
        }

        // Logcat receipt preview (no paper needed) — filter: ReturnReceiptPreview
//        ReturnReceiptLogPreview.log(_returnSaleRes, currency);

        // Toast.makeText(context, "return print 2", Toast.LENGTH_LONG).show();


        returnSaleRes = _returnSaleRes;

        Log.e("PrinterUtil_Debug", "printReturnReceiptData(): LowBattery = " + LowBattery + ", nopaper = " + nopaper);

        if (LowBattery) {
            Log.e("PrinterUtil_Debug", "printReturnReceiptData(): LowBattery is true! Sending LOWBATTERY message");
            handler.sendMessage(handler.obtainMessage(LOWBATTERY, 1, 0, null));
        } else {
            if (!nopaper) {
                // handler.sendMessage(handler.obtainMessage(PRINTPICTURE, 1, 0, null));

                Log.e("PrinterUtil_Debug", "printReturnReceiptData(): Paper is available. Sending PRINTCONTENT message");
                handler.sendMessage(handler.obtainMessage(PRINTCONTENT, 1, 0, null));
                //Toast.makeText(context, "Paper Available", Toast.LENGTH_LONG).show();

            } else {
                Log.e("PrinterUtil_Debug", "printReturnReceiptData(): No paper detected!");
                Toast.makeText(context, context.getString(R.string.toast_no_paper_detected), Toast.LENGTH_LONG).show();
            }
        }

    }

    public void printCopySaleReceiptData(SaleReceiptRes _copySaleReceiptRes) {
        printType = "COPY_SALE";
        Log.e("PrinterUtil_Debug", "printCopySaleReceiptData() called. printType = " + printType);
        if (_copySaleReceiptRes == null) {
            Log.e("PrinterUtil_Debug", "printCopySaleReceiptData(): SaleReceiptRes is NULL!");
        } else if (_copySaleReceiptRes.getData() != null) {
            Log.e("PrinterUtil_Debug", "printCopySaleReceiptData(): invoice_id = "
                    + _copySaleReceiptRes.getData().getInvoice_id());
        }

        copySaleReceiptRes = _copySaleReceiptRes;

        if (LowBattery) {
            handler.sendMessage(handler.obtainMessage(LOWBATTERY, 1, 0, null));
        } else if (!nopaper) {
            handler.sendMessage(handler.obtainMessage(PRINTCONTENT, 1, 0, null));
        } else {
            Toast.makeText(context, context.getString(R.string.toast_no_paper_detected), Toast.LENGTH_LONG).show();
        }
    }

//    public void printReceipt(@NotNull PosSalesDetails posSaleData) {
//
//        posSalesDetails = posSaleData;
//
//        if (LowBattery) {
//            handler.sendMessage(handler.obtainMessage(4, 1, 0, null));
//        } else {
//            if (!nopaper) {
//                handler.sendMessage(handler.obtainMessage(9, 1, 0, null));
//                Toast.makeText(context, "Paper Available", Toast.LENGTH_LONG).show();
//
//            } else {
//                Toast.makeText(context, context.getString(R.string.toast_no_paper_detected), Toast.LENGTH_LONG).show();
//            }
//        }
//
//    }

    private class MyHandler extends Handler {
        public void handleMessage(Message msg) {
            Log.e("PrinterUtil_Debug", "MyHandler.handleMessage() invoked. msg.what = " + msg.what);
            switch (msg.what) {
                case NOPAPER:
                    Log.e("PrinterUtil_Debug", "MyHandler: NOPAPER message received");
                    noPaperDlg();
                    break;
                case LOWBATTERY:
                    Log.e("PrinterUtil_Debug", "MyHandler: LOWBATTERY message received");
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                    alertDialog.setTitle(context.getString(R.string.operation_result));
                    alertDialog.setMessage(context.getString(R.string.LowBattery));
                    alertDialog.setPositiveButton("OK", (dialog, which) -> {
                    });
                    alertDialog.show();
                    break;
                case PRINTCONTENT:
                    Log.e("PrinterUtil_Debug", "MyHandler: PRINTCONTENT message received. Starting contentPrintThread...");
                    new contentPrintThread().start();
                    break;

                case PRINTPICTURE:
                    Log.e("PrinterUtil_Debug", "MyHandler: PRINTPICTURE message received. Starting printPicture thread...");
                    new printPicture().start();
                    break;

                case NOBLACKBLOCK:
                    Log.e("PrinterUtil_Debug", "MyHandler: NOBLACKBLOCK message received");
                    Toast.makeText(context, R.string.maker_not_find, Toast.LENGTH_SHORT).show();
                    break;
//                case 10:
//                    //CANCELPROMPT
//                    if (progressDialog != null && !UsbPrinterActivity.this.isFinishing()) {
//                        progressDialog.dismiss();
//                        progressDialog = null;
//                    }
//                    break;
                default:
                    Log.e("PrinterUtil_Debug", "MyHandler: default branch, msg.what = " + msg.what);
                    break;
            }
        }
    }

    private void noPaperDlg() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(context);
        dlg.setTitle(context.getString(R.string.noPaper));
        dlg.setMessage(context.getString(R.string.noPaperNotice));
        dlg.setCancelable(false);
        dlg.setPositiveButton("OK", (dialog, which) -> {
        });
        dlg.show();
    }



    private class printPicture extends Thread {

        public void run() {
            super.run();
            try {
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setGray(3);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                //File file = new File(picturePath);
                //if (file.exists()) {
                mUsbThermalPrinter.printLogo(drawableToBitmap(ContextCompat.getDrawable(context,R.drawable.mlogo)), false);
                mUsbThermalPrinter.walkPaper(20);
				/*} else {
					runOnUiThread(new Runnable() {


						public void run() {
							Toast.makeText(UsbPrinterActivity.this, getString(R.string.not_find_picture),
									Toast.LENGTH_LONG).show();
						}
					});
				}*/
            } catch (Exception e) {
                e.printStackTrace();
                Result = e.toString();
                if (Result.contains("NoPaperException")) {
                    nopaper = true;
                } else if (Result.contains("OverHeatException")) {
                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                } else {
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }
            } finally {
                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                if (nopaper) {
                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                    nopaper = false;
                    return;
                }
            }
        }
    }


    public Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            // If the drawable is a BitmapDrawable, just return its bitmap
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            // Otherwise, create a new bitmap and draw the drawable on it
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();

            // Ensure dimensions are valid, otherwise, use default dimensions
            width = width > 0 ? width : 1;
            height = height > 0 ? height : 1;

            // Create a bitmap with the specified width and height
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            // Create a canvas to draw on the bitmap
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }

        return bitmap;
    }


    private class contentPrintThread extends Thread {
        public void run() {
            super.run();
            Log.e("PrinterUtil_Debug", "contentPrintThread: run() started on thread " + Thread.currentThread().getName());
            Log.e("PrinterUtil_Debug", "contentPrintThread: printType = " + printType);
            if(printType.equals("SALE")){
                Log.e("PrinterUtil_Debug", "contentPrintThread: printType is SALE. Invoking printSaleType()...");
                printSaleType(posSalesDetails);
            }else if(printType.equals("RETURN")){
                Log.e("PrinterUtil_Debug", "contentPrintThread: printType is RETURN. Invoking printReturnType()...");
                printReturnType(returnSaleRes);
            } else if (printType.equals("COPY_SALE")) {
                Log.e("PrinterUtil_Debug", "contentPrintThread: printType is COPY_SALE. Invoking printSaleReceipt()...");
                printSaleReceipt(copySaleReceiptRes);
            } else {
                Log.e("PrinterUtil_Debug", "contentPrintThread: unknown printType: " + printType);
            }
        }
    }

    public void printReturnType(ReturnSaleRes details) {

        Log.e("PrinterUtil_Debug", "printReturnType() called");

        com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper orgHelper =
                new com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper(context);

        String receiptType = orgHelper.getOrganisationData().getReciept_type();

        Log.e("PrinterUtil_Debug",
                "printReturnType(): retrieved receiptType = '" + receiptType + "'");

        if (receiptType == null || receiptType.trim().isEmpty()) {
            receiptType = "rra";
        }

        if (receiptType.equalsIgnoreCase("rra")) {

            try {
            // ── Resolve rcptType once, safely ────────────────────────────
            String rcptType = (details.getData().getRcptType() != null)
                    ? details.getData().getRcptType() : "";
            Log.e("PrinterUtil_Debug", "printReturnType(): resolved rcptType = '" + rcptType + "'");

            Log.e("PrinterUtil_Debug", "printReturnType(): calling mUsbThermalPrinter.reset()...");
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setLeftIndent(1);
            mUsbThermalPrinter.setLineSpace(3);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.addString("CIS Version : 1.0.1");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(3);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

            Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.image22);
            logoBitmap = Bitmap.createScaledBitmap(logoBitmap, 400, 200, true);
            mUsbThermalPrinter.printLogo(logoBitmap, false);
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(32);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(26);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(24);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) + details.getData().getTpin_no());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ TOP: rcptType label (matches printSaleType pattern) â”€â”€â”€â”€â”€â”€
            if (rcptType.equalsIgnoreCase("P") || rcptType.equalsIgnoreCase("Proforma")) {
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_proforma).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            } else if (rcptType.equalsIgnoreCase("T") || rcptType.equalsIgnoreCase("Training")) {
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_training_mode));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            } else {
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            }
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.setTextSize(23);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund).toUpperCase(Locale.getDefault()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

            String refLabel = receiptStr(R.string.receipt_label_ref_normal);
            Double ogRcptNoVal = details.getData().getOgRcpt_no();
            String refValue = (ogRcptNoVal != null && !ogRcptNoVal.isNaN() && !ogRcptNoVal.isInfinite())
                    ? String.valueOf((long) ogRcptNoVal.doubleValue())
                    : "0";
            mUsbThermalPrinter.addString(refLabel + padLeft(refValue, TSIZE22 - refLabel.length()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund_approved_only));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(6);

            String buyerName = details.getData() != null && details.getData().getCustomer_name() != null
                    ? details.getData().getCustomer_name() : "N/A";
            String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
            mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
            mUsbThermalPrinter.printString();

            String buyerTin = (details.getData() != null && details.getData().getBuyers_tpin() != null)
                    ? details.getData().getBuyers_tpin() : "N/A";
            String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
            mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
            mUsbThermalPrinter.printString();

            String buyerContact = (details.getData() != null && details.getData().getCustomer_mob_no() != null)
                    ? details.getData().getCustomer_mob_no() : "N/A";
            String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
            mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ Items loop â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            for (ReturnedItem item : details.getData().getReturned_items()) {
                productname = item.getProduct_name();
                looseOil = productname.toLowerCase().startsWith("bulk oil");

                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(item.getProduct_name());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);

                String taxCode = "";
                if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                    taxCode = item.getTax_details().getCode();
                }

                String rateCol   = FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getRetail_price())) + "x";
                String qtyCol    = FunUtils.INSTANCE.DtoString(item.getReturn_quantity());
                String amountCol = "-" + FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_amount())) + taxCode;

                int totalWidth    = TSIZE22;
                int rightColWidth = 10;
                int midColWidth   = 8;
                int leftColWidth  = totalWidth - midColWidth - rightColWidth;

                String line2 = String.format(
                        "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                        rateCol, qtyCol, amountCol
                );
                mUsbThermalPrinter.addString(line2);
                mUsbThermalPrinter.printString();

                Double discount    = item.getDiscount();
                Double totalAmount = item.getTotal_amount();
                if (discount != null && discount > 0 && totalAmount != null && totalAmount > 0) {
                    Double discountPercent = (discount / totalAmount) * 100.0;
                    String discountText    = receiptStr(R.string.receipt_label_discount_percent, FunUtils.INSTANCE.DtoString(discountPercent));
                    double finalAmount     = totalAmount - discount;
                    String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(Double.toString(finalAmount));
                    String discountLine = String.format(
                            "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                            discountText, discountAmountStr
                    );
                    mUsbThermalPrinter.addString(discountLine);
                    mUsbThermalPrinter.printString();
                }
                mUsbThermalPrinter.walkPaper(1);
            }

            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ "THIS IS NOT AN OFFICIAL RECEIPT" (fixed: uses equalsIgnoreCase) â”€â”€
            if (rcptType.equalsIgnoreCase("P") || rcptType.equalsIgnoreCase("Proforma") ||
                    rcptType.equalsIgnoreCase("T") || rcptType.equalsIgnoreCase("Training") ||
                    rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            }

            // â”€â”€ TOTAL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.setTextSize(26);
            mUsbThermalPrinter.setBold(true);
            String label   = receiptStr(R.string.receipt_label_total) + "(" + currency + "):";
            String value   = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(details.getData().getGrand_total()));
            int padding    = TSIZE26 - label.length();
            mUsbThermalPrinter.addString(label + padLeft(value, padding));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setBold(false);

            List<TaxSummary> taxSummaryList = details.getData().getTax_summery();
            if (taxSummaryList != null && !taxSummaryList.isEmpty()) {
                for (TaxSummary taxSummary : taxSummaryList) {
                    String code         = taxSummary.getCode();
                    Double taxableValue = taxSummary.getTaxable_value();
                    if (code != null) {
                        String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                        mUsbThermalPrinter.addString(taxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                TSIZE22 - taxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }
                for (TaxSummary taxSummary : taxSummaryList) {
                    String code = taxSummary.getCode();
                    Double taxAmount  = taxSummary.getTax_amount();
                    if (taxAmount != null && taxAmount != 0.0) {
                        String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                        mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmount)),
                                TSIZE22 - taxAmountLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }
                Double totalTaxAmountVal = details.getData().getTax_amount();
                if (totalTaxAmountVal != null && totalTaxAmountVal != 0.0) {
                    String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                    mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                            FunUtils.INSTANCE.formatPrintPrice(String.valueOf(totalTaxAmountVal)),
                            TSIZE22 - totalTaxLabel.length()
                    ));
                    mUsbThermalPrinter.printString();
                }
            }

            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ MID-BOTTOM: rcptType label (matches printSaleType pattern) â”€â”€
            if (rcptType.equalsIgnoreCase("P") || rcptType.equalsIgnoreCase("Proforma")) {
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_proforma).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            } else if (rcptType.equalsIgnoreCase("T") || rcptType.equalsIgnoreCase("Training")) {
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_training_mode));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            } else {
                mUsbThermalPrinter.walkPaper(1);
            }

            // â”€â”€ Payment / Items count (skip for Proforma) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (!rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                String grandTotal = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(details.getData().getGrand_total()));
                String paymentLabel = receiptStr(R.string.payment_type_cash);
                mUsbThermalPrinter.addString(paymentLabel + padLeft(grandTotal, TSIZE22 - paymentLabel.length()));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(
                        Integer.toString(details.getData().getReturned_items().size()), TSIZE22 - 6
                ));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            } else {
                mUsbThermalPrinter.walkPaper(1); // maintain spacing
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            }

            if (rcptType.equalsIgnoreCase("P") ||
                    rcptType.equalsIgnoreCase("T") ||
                    rcptType.equalsIgnoreCase("C") ||
                    rcptType.equalsIgnoreCase("Proforma") ||
                    rcptType.equalsIgnoreCase("Training") ||
                    rcptType.equalsIgnoreCase("Copy")) {

                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            }


            // â”€â”€ SDC INFORMATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.setAlgin(1);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_sdc_info));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setAlgin(0);

            VsdcReceipt vsdc = (details.getData().getVsdc_reciept() != null && !details.getData().getVsdc_reciept().isEmpty())
                    ? details.getData().getVsdc_reciept().get(0) : null;

            String rawDateTime = (vsdc != null) ? vsdc.getVsdcRcptPbctDate() : "";
            String formattedDate = "", formattedTime = "";
            if (rawDateTime != null && !rawDateTime.isEmpty()) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                    Date parsedDate = inputFormat.parse(rawDateTime);
                    if (parsedDate != null) {
                        formattedDate = formatReceiptDate(parsedDate);
                        formattedTime = new SimpleDateFormat("HH:mm:ss",    Locale.getDefault()).format(parsedDate);
                    }
                } catch (ParseException e) {
                    Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                    formattedDate = rawDateTime;
                }
            }

            String dateLabel = receiptStr(R.string.receipt_label_date) + " " + formattedDate;
            String timeLabel = receiptStr(R.string.receipt_label_time) + " " + formattedTime;
            int spaces = TSIZE22 - dateLabel.length() - timeLabel.length();
            if (spaces < 1) spaces = 1;
            StringBuilder dateLine = new StringBuilder(dateLabel);
            for (int i = 0; i < spaces; i++) dateLine.append(" ");
            dateLine.append(timeLabel);
            mUsbThermalPrinter.addString(dateLine.toString());
            mUsbThermalPrinter.printString();

            String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
            String sdcIdValue = (vsdc != null && vsdc.getSdcId() != null)
                    ? vsdc.getSdcId() : "";
            mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcIdValue, TSIZE22 - sdcIdLabel.length()));
            mUsbThermalPrinter.printString();

            // Build receipttype code
            String rcptTypeValue = details.getData().getRcptType() != null ? details.getData().getRcptType() : "N";
            String receipttype;
            if      (rcptTypeValue.equalsIgnoreCase("N") || rcptTypeValue.equalsIgnoreCase("Normal"))   receipttype = "N";
            else if (rcptTypeValue.equalsIgnoreCase("P") || rcptTypeValue.equalsIgnoreCase("Proforma")) receipttype = "P";
            else if (rcptTypeValue.equalsIgnoreCase("T") || rcptTypeValue.equalsIgnoreCase("Training")) receipttype = "T";
            else if (rcptTypeValue.equalsIgnoreCase("C") || rcptTypeValue.equalsIgnoreCase("Copy"))     receipttype = "C";
            else receipttype = rcptTypeValue;

            String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
            String sdcRcptValue = (details.getData().getReturned_invoice_id() != null
                    ? details.getData().getReturned_invoice_id() : "")
                    + "/" + (vsdc != null ? vsdc.getTotRcptNo() : "")
                    + " " + receipttype + "R";
            mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
            mUsbThermalPrinter.printString();

            // â”€â”€ Internal Data + Signature + QR: SKIP for Proforma & Training â”€â”€
            if (!rcptType.equalsIgnoreCase("T") && !rcptType.equalsIgnoreCase("Training") &&
                    !rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {

                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + (vsdc != null ? vsdc.getIntrlData() : ""));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + (vsdc != null ? vsdc.getRcptSign() : ""));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                try {
                    if (vsdc != null &&
                            vsdc.getQrCodeUrl() != null &&
                            !vsdc.getQrCodeUrl().isEmpty()) {

                        mUsbThermalPrinter.reset();
                        mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                        String qrData   = vsdc.getQrCodeUrl();
                        Bitmap qrBitmap = generateQRCodeBitmap(qrData, 250, 250);

                        if (qrBitmap != null) {
                            mUsbThermalPrinter.setGray(6);
                            mUsbThermalPrinter.printLogo(qrBitmap, false);
                            mUsbThermalPrinter.walkPaper(2);
                        } else {
                            Log.w("PrinterUtil", "QR code bitmap is null");
                        }
                    } else {
                        Log.w("PrinterUtil", "No QR code URL available - skipping QR code");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("PrinterUtil", "Exception in QR code section: " + e.getMessage());
                }
            } // END skip block for Proforma / Training

            mUsbThermalPrinter.setAlgin(0);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
            String receiptNum   = (details.getData().getReturned_invoice_id() != null)
                    ? details.getData().getReturned_invoice_id() : "";
            mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
            mUsbThermalPrinter.printString();

            String rawReturnDateTime = (details.getData().getReturned_date() != null)
                    ? details.getData().getReturned_date() : "";
            String formattedReturnDate = "", formattedReturnTime = "";
            if (!rawReturnDateTime.isEmpty()) {
                // Try multiple formats: server uses 6-digit microseconds, offline uses 3-digit milliseconds
                String[] returnDateFormats = {
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd HH:mm:ss"
                };
                Date parsedReturnDate = null;
                for (String fmt : returnDateFormats) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.getDefault());
                        sdf.setLenient(false);
                        parsedReturnDate = sdf.parse(rawReturnDateTime);
                        if (parsedReturnDate != null) break;
                    } catch (ParseException ignored) {}
                }
                if (parsedReturnDate != null) {
                    formattedReturnDate = formatReceiptDate(parsedReturnDate);
                    formattedReturnTime = new SimpleDateFormat("HH:mm:ss",    Locale.getDefault()).format(parsedReturnDate);
                } else {
                    Log.e("DateFormat", "All return-date formats failed for: " + rawReturnDateTime);
                    formattedReturnDate = rawReturnDateTime;
                }
            }

            String returnDateLabel = receiptStr(R.string.receipt_label_date) + " " + formattedReturnDate;
            String returnTimeLabel = receiptStr(R.string.receipt_label_time) + " " + formattedReturnTime;
            int returnSpaces = TSIZE22 - returnDateLabel.length() - returnTimeLabel.length();
            if (returnSpaces < 1) returnSpaces = 1;
            StringBuilder returnDateLine = new StringBuilder(returnDateLabel);
            for (int i = 0; i < returnSpaces; i++) returnDateLine.append(" ");
            returnDateLine.append(returnTimeLabel);
            mUsbThermalPrinter.addString(returnDateLine.toString());
            mUsbThermalPrinter.printString();

            String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
            String mrcValue = (vsdc != null && vsdc.getMrcNo() != null)
                    ? vsdc.getMrcNo() + "." : ".";
            mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.setAlgin(0);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(3);

            mUsbThermalPrinter.setAlgin(1);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.addString("*** END ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(5);
            mUsbThermalPrinter.addString(" ");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(5);
            mUsbThermalPrinter.reset();

            } catch (Exception e) {

                Log.e("PrinterUtil_Debug",
                        "printReturnType(rra): Exception caught during printing", e);

                e.printStackTrace();

                Result = e.toString();

                if (Result.contains("NoPaperException")) {

                    nopaper = true;

                } else if (Result.contains("OverHeatException")) {

                    handler.sendMessage(
                            handler.obtainMessage(OVERHEAT, 1, 0, null));

                } else {

                    handler.sendMessage(
                            handler.obtainMessage(PRINTERR, 1, 0, null));
                }

            } finally {

                handler.sendMessage(
                        handler.obtainMessage(CANCELPROMPT, 1, 0, null));

                if (nopaper) {

                    handler.sendMessage(
                            handler.obtainMessage(NOPAPER, 1, 0, null));

                    nopaper = false;
                }
            }

        } else if (receiptType.equalsIgnoreCase("moz")) {

            // CALL MOZ RETURN LAYOUT
            // Add moz return print code here if needed
            try {

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setLeftIndent(1);
                mUsbThermalPrinter.setLineSpace(3);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("*** Internal Invoice ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(32);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);

                mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_credit_note));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(2);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.setTextSize(22);

                mUsbThermalPrinter.setGray(6);
                String tinLabel = context.getString(R.string.receipt_label_tin_no);
                mUsbThermalPrinter.addString(tinLabel + padLeft(details.getData().getTpin_no(), TSIZE22 - tinLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setGray(6);
                String dateLabel = receiptStr(R.string.receipt_label_date);
                mUsbThermalPrinter.addString(dateLabel + padLeft((details.getData().getReturned_date()), TSIZE22 - dateLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(2);

                mUsbThermalPrinter.setGray(6);
                String sdcLabel = context.getString(R.string.receipt_label_sdc_receipt_no);
                mUsbThermalPrinter.addString(sdcLabel + padLeft(details.getData().getReturned_invoice_id(), TSIZE22 - sdcLabel.length()));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setGray(6);
                String buyerNameLabel = context.getString(R.string.receipt_label_buyer_name);
                mUsbThermalPrinter.addString(buyerNameLabel + padLeft(
                        details.getData() != null && details.getData().getCustomer_name() != null
                                ? details.getData().getCustomer_name()
                                : "N/A",
                        TSIZE22 - buyerNameLabel.length()));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setGray(6);
                String buyerTinLabel = context.getString(R.string.receipt_label_buyer_tin);
                mUsbThermalPrinter.addString(buyerTinLabel + padLeft(details.getData().getBuyers_tpin() != null ? details.getData().getBuyers_tpin() : "N/A" , TSIZE22 - buyerTinLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(2);

                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_description));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_qty_rate_amount));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(2);

                for (ReturnedItem item : details.getData().getReturned_items()) {

                    productname = item.getProduct_name();

                    if (productname.toLowerCase().startsWith("bulk oil")) {

                    }

                    mUsbThermalPrinter.setTextSize(24);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.addString(item.getProduct_name());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(false);

                    mUsbThermalPrinter.setTextSize(22);

                    String qtyRate =
                            FunUtils.INSTANCE.DtoString(item.getReturn_quantity())
                                    + " X "
                                    + FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getRetail_price()));

                    String amount =
                            FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_returned_amount()));

                    String Line2 =
                            qtyRate + padLeft(amount, TSIZE22 - (qtyRate.length()));

                    mUsbThermalPrinter.addString(Line2);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }

                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setBold(true);

                String totalLabel = receiptStr(R.string.receipt_label_total) + " (" + currency + ") :";
                mUsbThermalPrinter.addString(
                        totalLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(Double.toString(details.getData().getTotal())),
                                TSIZE26 - totalLabel.length()));

                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.walkPaper(2);

                mUsbThermalPrinter.setTextSize(22);

                String itemsLabel = receiptStr(R.string.receipt_label_items);
                mUsbThermalPrinter.addString(
                        itemsLabel + padLeft(
                                Integer.toString(details.getData().getReturned_items().size()),
                                TSIZE22 - itemsLabel.length()));
                mUsbThermalPrinter.printString();

                String taxExLabel = receiptStr(R.string.receipt_label_tax_ex);
                mUsbThermalPrinter.addString(
                        taxExLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_ex()),
                                TSIZE22 - taxExLabel.length()));
                mUsbThermalPrinter.printString();

                String taxVatLabel = receiptStr(R.string.receipt_label_tax_vat);
                mUsbThermalPrinter.addString(
                        taxVatLabel + padLeft(
                                details.getData().getTax() + "%",
                                TSIZE22 - taxVatLabel.length()));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setBold(true);

                String totalVatLabel = receiptStr(R.string.receipt_label_total_vat);
                mUsbThermalPrinter.addString(
                        totalVatLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(
                                        Double.toString(details.getData().getTax_amount())
                                ),
                                TSIZE22 - totalVatLabel.length()));

                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setTextSize(26);

                String payableLabel = receiptStr(R.string.receipt_label_payable);
                mUsbThermalPrinter.addString(
                        payableLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(
                                        Double.toString(details.getData().getGrand_total())
                                ),
                                TSIZE26 - payableLabel.length()));

                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(20);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(2);

                mUsbThermalPrinter.setLeftIndent(1);
                mUsbThermalPrinter.setLineSpace(3);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.addString("*** END ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(15);

                mUsbThermalPrinter.reset();

            } catch (Exception e) {

                e.printStackTrace();
                System.out.println(e.toString());

                Result = e.toString();

                if (Result.contains("NoPaperException")) {

                    nopaper = true;

                } else if (Result.contains("OverHeatException")) {

                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));

                } else {

                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }

            } finally {

                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));

                if (nopaper) {

                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                    nopaper = false;
                    return;
                }
            }


        } else if (receiptType.equalsIgnoreCase("default")) {

            // CALL DEFAULT RETURN LAYOUT
            // Or reuse current RRA layout
            try {
                // ── Resolve rcptType once, safely ────────────────────────────
                String rcptType = (details.getData().getRcptType() != null)
                        ? details.getData().getRcptType() : "";
                Log.e("PrinterUtil_Debug", "printReturnType(): resolved rcptType = '" + rcptType + "'");

                Log.e("PrinterUtil_Debug", "printReturnType(): calling mUsbThermalPrinter.reset()...");
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setLeftIndent(1);
                mUsbThermalPrinter.setLineSpace(3);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.addString("CIS Version : 1.0.1");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.image22);
                logoBitmap = Bitmap.createScaledBitmap(logoBitmap, 400, 200, true);
//                mUsbThermalPrinter.printLogo(logoBitmap, false);
//                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(32);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) + details.getData().getTpin_no());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ TOP: rcptType label (matches printSaleType pattern) â”€â”€â”€â”€â”€â”€
                if (rcptType.equalsIgnoreCase("P") || rcptType.equalsIgnoreCase("Proforma")) {
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_proforma).toUpperCase(Locale.getDefault()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                } else if (rcptType.equalsIgnoreCase("T") || rcptType.equalsIgnoreCase("Training")) {
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_training_mode));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                } else {
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                }
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

                String refLabel = receiptStr(R.string.receipt_label_ref_normal);
                Double ogRcptNoVal = details.getData().getOgRcpt_no();
                String refValue = (ogRcptNoVal != null && !ogRcptNoVal.isNaN() && !ogRcptNoVal.isInfinite())
                        ? String.valueOf((long) ogRcptNoVal.doubleValue())
                        : "0";
                mUsbThermalPrinter.addString(refLabel + padLeft(refValue, TSIZE22 - refLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund_approved_only));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setGray(6);

                String buyerName = details.getData() != null && details.getData().getCustomer_name() != null
                        ? details.getData().getCustomer_name() : "N/A";
                String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
                mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerTin = (details.getData() != null && details.getData().getBuyers_tpin() != null)
                        ? details.getData().getBuyers_tpin() : "N/A";
                String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
                mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerContact = (details.getData() != null && details.getData().getCustomer_mob_no() != null)
                        ? details.getData().getCustomer_mob_no() : "N/A";
                String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
                mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ Items loop â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                for (ReturnedItem item : details.getData().getReturned_items()) {
                    productname = item.getProduct_name();
                    looseOil = productname.toLowerCase().startsWith("bulk oil");

                    mUsbThermalPrinter.setTextSize(24);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.addString(item.getProduct_name());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);

                    String taxCode = "";
                    if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                        taxCode = item.getTax_details().getCode();
                    }

                    String rateCol   = FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getRetail_price())) + "x";
                    String qtyCol    = FunUtils.INSTANCE.DtoString(item.getReturn_quantity());
                    String amountCol = "-" + FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_amount())) + taxCode;

                    int totalWidth    = TSIZE22;
                    int rightColWidth = 10;
                    int midColWidth   = 8;
                    int leftColWidth  = totalWidth - midColWidth - rightColWidth;

                    String line2 = String.format(
                            "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                            rateCol, qtyCol, amountCol
                    );
                    mUsbThermalPrinter.addString(line2);
                    mUsbThermalPrinter.printString();

                    Double discount    = item.getDiscount();
                    Double totalAmount = item.getTotal_amount();
                    if (discount != null && discount > 0 && totalAmount != null && totalAmount > 0) {
                        Double discountPercent = (discount / totalAmount) * 100.0;
                        String discountText    = receiptStr(R.string.receipt_label_discount_percent, FunUtils.INSTANCE.DtoString(discountPercent));
                        double finalAmount     = totalAmount - discount;
                        String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(Double.toString(finalAmount));
                        String discountLine = String.format(
                                "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                discountText, discountAmountStr
                        );
                        mUsbThermalPrinter.addString(discountLine);
                        mUsbThermalPrinter.printString();
                    }
                    mUsbThermalPrinter.walkPaper(1);
                }

                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ "THIS IS NOT AN OFFICIAL RECEIPT" (fixed: uses equalsIgnoreCase) â”€â”€
                if (rcptType.equalsIgnoreCase("P") || rcptType.equalsIgnoreCase("Proforma") ||
                        rcptType.equalsIgnoreCase("T") || rcptType.equalsIgnoreCase("Training") ||
                        rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(23);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }

                // â”€â”€ TOTAL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setBold(true);
                String label   = receiptStr(R.string.receipt_label_total) + "(" + currency + "):";
                String value   = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(details.getData().getGrand_total()));
                int padding    = TSIZE26 - label.length();
                mUsbThermalPrinter.addString(label + padLeft(value, padding));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);

                List<TaxSummary> taxSummaryList = details.getData().getTax_summery();
                if (taxSummaryList != null && !taxSummaryList.isEmpty()) {
                    for (TaxSummary taxSummary : taxSummaryList) {
                        String code         = taxSummary.getCode();
                        Double taxableValue = taxSummary.getTaxable_value();
                        if (code != null) {
                            String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                            mUsbThermalPrinter.addString(taxLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                    TSIZE22 - taxLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }
                    for (TaxSummary taxSummary : taxSummaryList) {
                        String code = taxSummary.getCode();
                        Double taxAmount  = taxSummary.getTax_amount();
                        if (taxAmount != null && taxAmount != 0.0) {
                            String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                            mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmount)),
                                    TSIZE22 - taxAmountLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }
                    Double totalTaxAmountVal = details.getData().getTax_amount();
                    if (totalTaxAmountVal != null && totalTaxAmountVal != 0.0) {
                        String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                        mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(String.valueOf(totalTaxAmountVal)),
                                TSIZE22 - totalTaxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ MID-BOTTOM: rcptType label (matches printSaleType pattern) â”€â”€
                if (rcptType.equalsIgnoreCase("P") || rcptType.equalsIgnoreCase("Proforma")) {
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(23);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_proforma).toUpperCase(Locale.getDefault()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                } else if (rcptType.equalsIgnoreCase("T") || rcptType.equalsIgnoreCase("Training")) {
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(23);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_training_mode));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(23);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                } else {
                    mUsbThermalPrinter.walkPaper(1);
                }

                // â”€â”€ Payment / Items count (skip for Proforma) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                if (!rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    String grandTotal = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(details.getData().getGrand_total()));
                    String paymentLabel = receiptStr(R.string.payment_type_cash);
                    mUsbThermalPrinter.addString(paymentLabel + padLeft(grandTotal, TSIZE22 - paymentLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(
                            Integer.toString(details.getData().getReturned_items().size()), TSIZE22 - 6
                    ));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                } else {
                    mUsbThermalPrinter.walkPaper(1); // maintain spacing
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }

                if (rcptType.equalsIgnoreCase("P") ||
                        rcptType.equalsIgnoreCase("T") ||
                        rcptType.equalsIgnoreCase("C") ||
                        rcptType.equalsIgnoreCase("Proforma") ||
                        rcptType.equalsIgnoreCase("Training") ||
                        rcptType.equalsIgnoreCase("Copy")) {

                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }


                // â”€â”€ SDC INFORMATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//                mUsbThermalPrinter.setAlgin(1);
//                mUsbThermalPrinter.setBold(true);
//                mUsbThermalPrinter.setTextSize(22);
//                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_sdc_info));
//                mUsbThermalPrinter.printString();
//                mUsbThermalPrinter.walkPaper(1);
//
//                mUsbThermalPrinter.setGray(6);
//                mUsbThermalPrinter.setBold(false);
//                mUsbThermalPrinter.setAlgin(0);
//
//                VsdcReceipt vsdc = (details.getData().getVsdc_reciept() != null && !details.getData().getVsdc_reciept().isEmpty())
//                        ? details.getData().getVsdc_reciept().get(0) : null;
//
//                String rawDateTime = (vsdc != null) ? vsdc.getVsdcRcptPbctDate() : "";
//                String formattedDate = "", formattedTime = "";
//                if (rawDateTime != null && !rawDateTime.isEmpty()) {
//                    try {
//                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
//                        Date parsedDate = inputFormat.parse(rawDateTime);
//                        if (parsedDate != null) {
//                            formattedDate = formatReceiptDate(parsedDate);
//                            formattedTime = new SimpleDateFormat("HH:mm:ss",    Locale.getDefault()).format(parsedDate);
//                        }
//                    } catch (ParseException e) {
//                        Log.e("DateFormat", "Parsing failed: " + e.getMessage());
//                        formattedDate = rawDateTime;
//                    }
//                }
//
//                String dateLabel = receiptStr(R.string.receipt_label_date) + " " + formattedDate;
//                String timeLabel = receiptStr(R.string.receipt_label_time) + " " + formattedTime;
//                int spaces = TSIZE22 - dateLabel.length() - timeLabel.length();
//                if (spaces < 1) spaces = 1;
//                StringBuilder dateLine = new StringBuilder(dateLabel);
//                for (int i = 0; i < spaces; i++) dateLine.append(" ");
//                dateLine.append(timeLabel);
//                mUsbThermalPrinter.addString(dateLine.toString());
//                mUsbThermalPrinter.printString();
//
//                String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
//                String sdcIdValue = (vsdc != null && vsdc.getSdcId() != null)
//                        ? vsdc.getSdcId() : "";
//                mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcIdValue, TSIZE22 - sdcIdLabel.length()));
//                mUsbThermalPrinter.printString();
//
//                // Build receipttype code
//                String rcptTypeValue = details.getData().getRcptType() != null ? details.getData().getRcptType() : "N";
//                String receipttype;
//                if      (rcptTypeValue.equalsIgnoreCase("N") || rcptTypeValue.equalsIgnoreCase("Normal"))   receipttype = "N";
//                else if (rcptTypeValue.equalsIgnoreCase("P") || rcptTypeValue.equalsIgnoreCase("Proforma")) receipttype = "P";
//                else if (rcptTypeValue.equalsIgnoreCase("T") || rcptTypeValue.equalsIgnoreCase("Training")) receipttype = "T";
//                else if (rcptTypeValue.equalsIgnoreCase("C") || rcptTypeValue.equalsIgnoreCase("Copy"))     receipttype = "C";
//                else receipttype = rcptTypeValue;
//
//                String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
//                String sdcRcptValue = (details.getData().getReturned_invoice_id() != null
//                        ? details.getData().getReturned_invoice_id() : "")
//                        + "/" + (vsdc != null ? vsdc.getTotRcptNo() : "")
//                        + " " + receipttype + "R";
//                mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
//                mUsbThermalPrinter.printString();
//
//                // â”€â”€ Internal Data + Signature + QR: SKIP for Proforma & Training â”€â”€
//                if (!rcptType.equalsIgnoreCase("T") && !rcptType.equalsIgnoreCase("Training") &&
//                        !rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
//
//                    mUsbThermalPrinter.setAlgin(1);
//                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + (vsdc != null ? vsdc.getIntrlData() : ""));
//                    mUsbThermalPrinter.printString();
//                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + (vsdc != null ? vsdc.getRcptSign() : ""));
//                    mUsbThermalPrinter.printString();
//                    mUsbThermalPrinter.walkPaper(1);
//
//                    try {
//                        if (vsdc != null &&
//                                vsdc.getQrCodeUrl() != null &&
//                                !vsdc.getQrCodeUrl().isEmpty()) {
//
//                            mUsbThermalPrinter.reset();
//                            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
//
//                            String qrData   = vsdc.getQrCodeUrl();
//                            Bitmap qrBitmap = generateQRCodeBitmap(qrData, 250, 250);
//
//                            if (qrBitmap != null) {
//                                mUsbThermalPrinter.setGray(6);
//                                mUsbThermalPrinter.printLogo(qrBitmap, false);
//                                mUsbThermalPrinter.walkPaper(2);
//                            } else {
//                                Log.w("PrinterUtil", "QR code bitmap is null");
//                            }
//                        } else {
//                            Log.w("PrinterUtil", "No QR code URL available - skipping QR code");
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        Log.e("PrinterUtil", "Exception in QR code section: " + e.getMessage());
//                    }
//                } // END skip block for Proforma / Training
//
//                mUsbThermalPrinter.setAlgin(0);
//                mUsbThermalPrinter.setTextSize(22);
//                mUsbThermalPrinter.setGray(6);
//                mUsbThermalPrinter.addString("----------------------------------");
//                mUsbThermalPrinter.printString();
//                mUsbThermalPrinter.walkPaper(1);

                String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                String receiptNum   = (details.getData().getReturned_invoice_id() != null)
                        ? details.getData().getReturned_invoice_id() : "";
                mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
                mUsbThermalPrinter.printString();

                String rawReturnDateTime = (details.getData().getReturned_date() != null)
                        ? details.getData().getReturned_date() : "";
                String formattedReturnDate = "", formattedReturnTime = "";
                if (!rawReturnDateTime.isEmpty()) {
                    // Try multiple formats: server uses 6-digit microseconds, offline uses 3-digit milliseconds
                    String[] returnDateFormats = {
                            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                            "yyyy-MM-dd'T'HH:mm:ss'Z'",
                            "yyyy-MM-dd HH:mm:ss"
                    };
                    Date parsedReturnDate = null;
                    for (String fmt : returnDateFormats) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.getDefault());
                            sdf.setLenient(false);
                            parsedReturnDate = sdf.parse(rawReturnDateTime);
                            if (parsedReturnDate != null) break;
                        } catch (ParseException ignored) {}
                    }
                    if (parsedReturnDate != null) {
                        formattedReturnDate = formatReceiptDate(parsedReturnDate);
                        formattedReturnTime = new SimpleDateFormat("HH:mm:ss",    Locale.getDefault()).format(parsedReturnDate);
                    } else {
                        Log.e("DateFormat", "All return-date formats failed for: " + rawReturnDateTime);
                        formattedReturnDate = rawReturnDateTime;
                    }
                }

                String returnDateLabel = receiptStr(R.string.receipt_label_date) + " " + formattedReturnDate;
                String returnTimeLabel = receiptStr(R.string.receipt_label_time) + " " + formattedReturnTime;
                int returnSpaces = TSIZE22 - returnDateLabel.length() - returnTimeLabel.length();
                if (returnSpaces < 1) returnSpaces = 1;
                StringBuilder returnDateLine = new StringBuilder(returnDateLabel);
                for (int i = 0; i < returnSpaces; i++) returnDateLine.append(" ");
                returnDateLine.append(returnTimeLabel);
                mUsbThermalPrinter.addString(returnDateLine.toString());
                mUsbThermalPrinter.printString();

//                String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
//                String mrcValue = (vsdc != null && vsdc.getMrcNo() != null)
//                        ? vsdc.getMrcNo() + "." : ".";
//                mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
//                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setAlgin(0);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.addString("*** END ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.addString(" ");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.reset();

            } catch (Exception e) {

                Log.e("PrinterUtil_Debug",
                        "printReturnType(rra): Exception caught during printing", e);

                e.printStackTrace();

                Result = e.toString();

                if (Result.contains("NoPaperException")) {

                    nopaper = true;

                } else if (Result.contains("OverHeatException")) {

                    handler.sendMessage(
                            handler.obtainMessage(OVERHEAT, 1, 0, null));

                } else {

                    handler.sendMessage(
                            handler.obtainMessage(PRINTERR, 1, 0, null));
                }

            } finally {

                handler.sendMessage(
                        handler.obtainMessage(CANCELPROMPT, 1, 0, null));

                if (nopaper) {

                    handler.sendMessage(
                            handler.obtainMessage(NOPAPER, 1, 0, null));

                    nopaper = false;
                }
            }

        }else if (receiptType.equalsIgnoreCase("zra")) {


                try {
                    // ── Resolve rcptType once, safely ────────────────────────────
                    String rcptType = (details.getData().getRcptType() != null)
                            ? details.getData().getRcptType() : "";
                    Log.e("PrinterUtil_Debug", "printReturnType(): resolved rcptType = '" + rcptType + "'");

                    Log.e("PrinterUtil_Debug", "printReturnType(): calling mUsbThermalPrinter.reset()...");
                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setLeftIndent(1);
                    mUsbThermalPrinter.setLineSpace(3);
                    mUsbThermalPrinter.setTextSize(20);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.addString("CIS Version : 1.0.1");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(3);
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                    Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.image22);
                    logoBitmap = Bitmap.createScaledBitmap(logoBitmap, 400, 200, true);
                    mUsbThermalPrinter.printLogo(logoBitmap, false);
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(32);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(26);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(24);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) + details.getData().getTpin_no());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ TOP: rcptType label (matches printSaleType pattern) â”€â”€â”€â”€â”€â”€
                    if (rcptType.equalsIgnoreCase("P") || rcptType.equalsIgnoreCase("Proforma")) {
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_proforma).toUpperCase(Locale.getDefault()));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    } else if (rcptType.equalsIgnoreCase("T") || rcptType.equalsIgnoreCase("Training")) {
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_training_mode));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    } else {
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    }
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(23);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund).toUpperCase(Locale.getDefault()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

                    String refLabel = receiptStr(R.string.receipt_label_ref_normal);
                    Double ogRcptNoVal = details.getData().getOgRcpt_no();
                    String refValue = (ogRcptNoVal != null && !ogRcptNoVal.isNaN() && !ogRcptNoVal.isInfinite())
                            ? String.valueOf((long) ogRcptNoVal.doubleValue())
                            : "0";
                    mUsbThermalPrinter.addString(refLabel + padLeft(refValue, TSIZE22 - refLabel.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund_approved_only));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setGray(6);

                    String buyerName = details.getData() != null && details.getData().getCustomer_name() != null
                            ? details.getData().getCustomer_name() : "N/A";
                    String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
                    mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
                    mUsbThermalPrinter.printString();

                    String buyerTin = (details.getData() != null && details.getData().getBuyers_tpin() != null)
                            ? details.getData().getBuyers_tpin() : "N/A";
                    String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
                    mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
                    mUsbThermalPrinter.printString();

                    String buyerContact = (details.getData() != null && details.getData().getCustomer_mob_no() != null)
                            ? details.getData().getCustomer_mob_no() : "N/A";
                    String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
                    mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ Items loop â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    for (ReturnedItem item : details.getData().getReturned_items()) {
                        productname = item.getProduct_name();
                        looseOil = productname.toLowerCase().startsWith("bulk oil");

                        mUsbThermalPrinter.setTextSize(24);
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.addString(item.getProduct_name());
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);

                        String taxCode = "";
                        if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                            taxCode = item.getTax_details().getCode();
                        }

                        String rateCol   = FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getRetail_price())) + "x";
                        String qtyCol    = FunUtils.INSTANCE.DtoString(item.getReturn_quantity());
                        String amountCol = "-" + FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_amount())) + taxCode;

                        int totalWidth    = TSIZE22;
                        int rightColWidth = 10;
                        int midColWidth   = 8;
                        int leftColWidth  = totalWidth - midColWidth - rightColWidth;

                        String line2 = String.format(
                                "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                                rateCol, qtyCol, amountCol
                        );
                        mUsbThermalPrinter.addString(line2);
                        mUsbThermalPrinter.printString();

                        Double discount    = item.getDiscount();
                        Double totalAmount = item.getTotal_amount();
                        if (discount != null && discount > 0 && totalAmount != null && totalAmount > 0) {
                            Double discountPercent = (discount / totalAmount) * 100.0;
                            String discountText    = receiptStr(R.string.receipt_label_discount_percent, FunUtils.INSTANCE.DtoString(discountPercent));
                            double finalAmount     = totalAmount - discount;
                            String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(Double.toString(finalAmount));
                            String discountLine = String.format(
                                    "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                    discountText, discountAmountStr
                            );
                            mUsbThermalPrinter.addString(discountLine);
                            mUsbThermalPrinter.printString();
                        }
                        mUsbThermalPrinter.walkPaper(1);
                    }

                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ "THIS IS NOT AN OFFICIAL RECEIPT" (fixed: uses equalsIgnoreCase) â”€â”€
                    if (rcptType.equalsIgnoreCase("P") || rcptType.equalsIgnoreCase("Proforma") ||
                            rcptType.equalsIgnoreCase("T") || rcptType.equalsIgnoreCase("Training") ||
                            rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(23);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    }

                    // â”€â”€ TOTAL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    mUsbThermalPrinter.setTextSize(26);
                    mUsbThermalPrinter.setBold(true);
                    String label   = receiptStr(R.string.receipt_label_total) + "(" + currency + "):";
                    String value   = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(details.getData().getGrand_total()));
                    int padding    = TSIZE26 - label.length();
                    mUsbThermalPrinter.addString(label + padLeft(value, padding));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setBold(false);

                    List<TaxSummary> taxSummaryList = details.getData().getTax_summery();
                    if (taxSummaryList != null && !taxSummaryList.isEmpty()) {
                        for (TaxSummary taxSummary : taxSummaryList) {
                            String code         = taxSummary.getCode();
                            Double taxableValue = taxSummary.getTaxable_value();
                            if (code != null) {
                                String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                                mUsbThermalPrinter.addString(taxLabel + padLeft(
                                        FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                        TSIZE22 - taxLabel.length()
                                ));
                                mUsbThermalPrinter.printString();
                            }
                        }
                        for (TaxSummary taxSummary : taxSummaryList) {
                            String code = taxSummary.getCode();
                            Double taxAmount  = taxSummary.getTax_amount();
                            if (taxAmount != null && taxAmount != 0.0) {
                                String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                                mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                        FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmount)),
                                        TSIZE22 - taxAmountLabel.length()
                                ));
                                mUsbThermalPrinter.printString();
                            }
                        }
                        Double totalTaxAmountVal = details.getData().getTax_amount();
                        if (totalTaxAmountVal != null && totalTaxAmountVal != 0.0) {
                            String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                            mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(totalTaxAmountVal)),
                                    TSIZE22 - totalTaxLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ MID-BOTTOM: rcptType label (matches printSaleType pattern) â”€â”€
                    if (rcptType.equalsIgnoreCase("P") || rcptType.equalsIgnoreCase("Proforma")) {
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(23);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_proforma).toUpperCase(Locale.getDefault()));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    } else if (rcptType.equalsIgnoreCase("T") || rcptType.equalsIgnoreCase("Training")) {
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(23);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_training_mode));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(23);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    } else {
                        mUsbThermalPrinter.walkPaper(1);
                    }

                    // â”€â”€ Payment / Items count (skip for Proforma) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    if (!rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);
                        String grandTotal = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(details.getData().getGrand_total()));
                        String paymentLabel = receiptStr(R.string.payment_type_cash);
                        mUsbThermalPrinter.addString(paymentLabel + padLeft(grandTotal, TSIZE22 - paymentLabel.length()));
                        mUsbThermalPrinter.printString();

                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(
                                Integer.toString(details.getData().getReturned_items().size()), TSIZE22 - 6
                        ));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    } else {
                        mUsbThermalPrinter.walkPaper(1); // maintain spacing
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    }

                    if (rcptType.equalsIgnoreCase("P") ||
                            rcptType.equalsIgnoreCase("T") ||
                            rcptType.equalsIgnoreCase("C") ||
                            rcptType.equalsIgnoreCase("Proforma") ||
                            rcptType.equalsIgnoreCase("Training") ||
                            rcptType.equalsIgnoreCase("Copy")) {

                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    }


                    // â”€â”€ SDC INFORMATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_sdc_info));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setAlgin(0);

                    VsdcReceipt vsdc = (details.getData().getVsdc_reciept() != null && !details.getData().getVsdc_reciept().isEmpty())
                            ? details.getData().getVsdc_reciept().get(0) : null;

                    String rawDateTime = (vsdc != null) ? vsdc.getVsdcRcptPbctDate() : "";
                    String formattedDate = "", formattedTime = "";
                    if (rawDateTime != null && !rawDateTime.isEmpty()) {
                        try {
                            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                            Date parsedDate = inputFormat.parse(rawDateTime);
                            if (parsedDate != null) {
                                formattedDate = formatReceiptDate(parsedDate);
                                formattedTime = new SimpleDateFormat("HH:mm:ss",    Locale.getDefault()).format(parsedDate);
                            }
                        } catch (ParseException e) {
                            Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                            formattedDate = rawDateTime;
                        }
                    }

                    String dateLabel = receiptStr(R.string.receipt_label_date) + " " + formattedDate;
                    String timeLabel = receiptStr(R.string.receipt_label_time) + " " + formattedTime;
                    int spaces = TSIZE22 - dateLabel.length() - timeLabel.length();
                    if (spaces < 1) spaces = 1;
                    StringBuilder dateLine = new StringBuilder(dateLabel);
                    for (int i = 0; i < spaces; i++) dateLine.append(" ");
                    dateLine.append(timeLabel);
                    mUsbThermalPrinter.addString(dateLine.toString());
                    mUsbThermalPrinter.printString();

                    String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                    String sdcIdValue = (vsdc != null && vsdc.getSdcId() != null)
                            ? vsdc.getSdcId() : "";
                    mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcIdValue, TSIZE22 - sdcIdLabel.length()));
                    mUsbThermalPrinter.printString();

                    // Build receipttype code
                    String rcptTypeValue = details.getData().getRcptType() != null ? details.getData().getRcptType() : "N";
                    String receipttype;
                    if      (rcptTypeValue.equalsIgnoreCase("N") || rcptTypeValue.equalsIgnoreCase("Normal"))   receipttype = "N";
                    else if (rcptTypeValue.equalsIgnoreCase("P") || rcptTypeValue.equalsIgnoreCase("Proforma")) receipttype = "P";
                    else if (rcptTypeValue.equalsIgnoreCase("T") || rcptTypeValue.equalsIgnoreCase("Training")) receipttype = "T";
                    else if (rcptTypeValue.equalsIgnoreCase("C") || rcptTypeValue.equalsIgnoreCase("Copy"))     receipttype = "C";
                    else receipttype = rcptTypeValue;

                    String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
                    String sdcRcptValue = (details.getData().getReturned_invoice_id() != null
                            ? details.getData().getReturned_invoice_id() : "")
                            + "/" + (vsdc != null ? vsdc.getTotRcptNo() : "")
                            + " " + receipttype + "R";
                    mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
                    mUsbThermalPrinter.printString();

                    // â”€â”€ Internal Data + Signature + QR: SKIP for Proforma & Training â”€â”€
                    if (!rcptType.equalsIgnoreCase("T") && !rcptType.equalsIgnoreCase("Training") &&
                            !rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {

                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + (vsdc != null ? vsdc.getIntrlData() : ""));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + (vsdc != null ? vsdc.getRcptSign() : ""));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);

                        try {
                            if (vsdc != null &&
                                    vsdc.getQrCodeUrl() != null &&
                                    !vsdc.getQrCodeUrl().isEmpty()) {

                                mUsbThermalPrinter.reset();
                                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                                String qrData   = vsdc.getQrCodeUrl();
                                Bitmap qrBitmap = generateQRCodeBitmap(qrData, 250, 250);

                                if (qrBitmap != null) {
                                    mUsbThermalPrinter.setGray(6);
                                    mUsbThermalPrinter.printLogo(qrBitmap, false);
                                    mUsbThermalPrinter.walkPaper(2);
                                } else {
                                    Log.w("PrinterUtil", "QR code bitmap is null");
                                }
                            } else {
                                Log.w("PrinterUtil", "No QR code URL available - skipping QR code");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("PrinterUtil", "Exception in QR code section: " + e.getMessage());
                        }
                    } // END skip block for Proforma / Training

                    mUsbThermalPrinter.setAlgin(0);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                    String receiptNum   = (details.getData().getReturned_invoice_id() != null)
                            ? details.getData().getReturned_invoice_id() : "";
                    mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
                    mUsbThermalPrinter.printString();

                    String rawReturnDateTime = (details.getData().getReturned_date() != null)
                            ? details.getData().getReturned_date() : "";
                    String formattedReturnDate = "", formattedReturnTime = "";
                    if (!rawReturnDateTime.isEmpty()) {
                        // Try multiple formats: server uses 6-digit microseconds, offline uses 3-digit milliseconds
                        String[] returnDateFormats = {
                                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                "yyyy-MM-dd HH:mm:ss"
                        };
                        Date parsedReturnDate = null;
                        for (String fmt : returnDateFormats) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.getDefault());
                                sdf.setLenient(false);
                                parsedReturnDate = sdf.parse(rawReturnDateTime);
                                if (parsedReturnDate != null) break;
                            } catch (ParseException ignored) {}
                        }
                        if (parsedReturnDate != null) {
                            formattedReturnDate = formatReceiptDate(parsedReturnDate);
                            formattedReturnTime = new SimpleDateFormat("HH:mm:ss",    Locale.getDefault()).format(parsedReturnDate);
                        } else {
                            Log.e("DateFormat", "All return-date formats failed for: " + rawReturnDateTime);
                            formattedReturnDate = rawReturnDateTime;
                        }
                    }

                    String returnDateLabel = receiptStr(R.string.receipt_label_date) + " " + formattedReturnDate;
                    String returnTimeLabel = receiptStr(R.string.receipt_label_time) + " " + formattedReturnTime;
                    int returnSpaces = TSIZE22 - returnDateLabel.length() - returnTimeLabel.length();
                    if (returnSpaces < 1) returnSpaces = 1;
                    StringBuilder returnDateLine = new StringBuilder(returnDateLabel);
                    for (int i = 0; i < returnSpaces; i++) returnDateLine.append(" ");
                    returnDateLine.append(returnTimeLabel);
                    mUsbThermalPrinter.addString(returnDateLine.toString());
                    mUsbThermalPrinter.printString();

                    String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
                    String mrcValue = (vsdc != null && vsdc.getMrcNo() != null)
                            ? vsdc.getMrcNo() + "." : ".";
                    mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(0);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(3);

                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.setTextSize(20);
                    mUsbThermalPrinter.addString("*** END ***");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(5);
                    mUsbThermalPrinter.addString(" ");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(5);
                    mUsbThermalPrinter.reset();

                } catch (Exception e) {

                    Log.e("PrinterUtil_Debug",
                            "printReturnType(rra): Exception caught during printing", e);

                    e.printStackTrace();

                    Result = e.toString();

                    if (Result.contains("NoPaperException")) {

                        nopaper = true;

                    } else if (Result.contains("OverHeatException")) {

                        handler.sendMessage(
                                handler.obtainMessage(OVERHEAT, 1, 0, null));

                    } else {

                        handler.sendMessage(
                                handler.obtainMessage(PRINTERR, 1, 0, null));
                    }

                } finally {

                    handler.sendMessage(
                            handler.obtainMessage(CANCELPROMPT, 1, 0, null));

                    if (nopaper) {

                        handler.sendMessage(
                                handler.obtainMessage(NOPAPER, 1, 0, null));

                        nopaper = false;
                    }
                }

            }
    }



    /// for debug invoice print
   /* private void printReturnType(ReturnSaleRes   details) {

        try {
            String printStr = "";

            Log.d("Line 1", "* START OF LEGEAL RECEIPT *");
            printStr = details.getData().getStore().getStore_name().toString().toUpperCase();
            Log.d("Line 1", printStr);


            printStr = details.getData().getStore().getAddress().toString().toUpperCase();
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_vat_no) + padLeft(details.getData().getVat_no(), TSIZE22-7);
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_tpin_no) + padLeft(details.getData().getTpin_no(), TSIZE22-8);
            Log.d("Line 1", printStr);
            //printStr = context.getString(R.string.receipt_label_date) + padLeft(DateTimeFormatting.Companion.formatSaleReturndate(details.getData().getReturned_date(), zone, context), TSIZE22-7);
            printStr = context.getString(R.string.receipt_label_date) + padLeft(details.getData().getReturned_date(), TSIZE22-7);
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_receipt_no_short) + padLeft(details.getData().getReturned_invoice_id(), TSIZE22-11);
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_buyer_name) + padLeft(details.getData() != null && details.getData().getCustomer_name() != null ? details.getData().getCustomer_name() : "", TSIZE22-13);
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_buyer_tin) + padLeft(details.getData().getBuyers_tpin(), TSIZE22-13);
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_description);
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_qty_rate_amount);
            Log.d("Line 1", printStr);

            printStr = "----------------------------------";
            Log.d("Line 1", printStr);


            for ( ReturnedItem item : details.getData().getReturned_items()){
                printStr = item.getProduct_name();
                Log.d("Line 1", printStr);

                //String qtyRate = FunUtils.INSTANCE.DtoString(item.getQuantity()) + " X " + FunUtils.INSTANCE.formatPrintPrice(item.getRetail_price());
                String qtyRate = FunUtils.INSTANCE.DtoString(item.getReturn_quantity())+" X " + FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getRetail_price()));
                String amount = FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_returned_amount()));
                String Line2 = qtyRate + padLeft(amount, TSIZE22 - (qtyRate.length()));
                printStr = Line2;
                Log.d("Line 1", printStr);
            }

            printStr = "----------------------------------";
            Log.d("Line 1", printStr);


            printStr = "TOTAL (" + currency + ") :" + padLeft(FunUtils.INSTANCE.formatPrintPrice(Double.toString(details.getData().getTotal())), TSIZE26 - (("TOTAL (" + currency + ") :").length()));
            Log.d("Line 1", printStr);


            printStr = context.getString(R.string.receipt_label_items) + padLeft(Integer.toString(details.getData().getReturned_items().size()), TSIZE22 - 6 );
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_tax_ex) + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_ex()), TSIZE22 - 7 );
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_tax_vat) + padLeft(details.getData().getTax()+"%", TSIZE22 - 9 );
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_total_vat) + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_amount()), TSIZE22 - 10 );
            Log.d("Line 1", printStr);
            printStr = "----------------------------------";
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_payable) + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()), TSIZE26-8);
            Log.d("Line 1", printStr);



//            String cashAmt = details.getData().getPayment_type().trim().toUpperCase().equals("CASH") ?
//                    FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()) : "";
//            printStr = receiptStr(R.string.payment_type_cash) + padLeft(cashAmt, TSIZE20 - 5);
//            Log.d("Line 1", printStr);
//            String nonCashAmt = details.getData().getPayment_type().trim().toUpperCase().equals("CARD") ||
//                    details.getData().getPayment_type().trim().toUpperCase().equals("M-MONEY") ?
//                    FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()) : "";
//            printStr = context.getString(R.string.receipt_label_mmoney_receipt) + padLeft(nonCashAmt, TSIZE20 - 8);
//            Log.d("Line 1", printStr);

            printStr = "----------------------------------";
            Log.d("Line 1", printStr);


            printStr = context.getString(R.string.receipt_label_sign_stamp);
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_end_of_receipt);
            Log.d("Line 1", printStr);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.toString());

            Result = e.toString();
            if (Result.contains("NoPaperException")) {
                nopaper = true;
            } else if (Result.contains("OverHeatException")) {
                handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
            } else {
                handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
            }
        }finally {
            handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
            if (nopaper) {
                handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                nopaper = false;
                return;
            }
        }

    }*/


    public static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    public static String padLeft(String s, int n) {
        return String.format("%" + n + "s", s);
    }

    private boolean hasSpotDiscount(String amount) {
        if (amount == null || amount.trim().isEmpty()) return false;
        try {
            double parsed = Double.parseDouble(amount.replace(",", "").trim());
            return Math.abs(parsed) > 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /// for debug invoice print

    /*private void printSaleType(PosSalesDetails details) {

        try {
            String printStr = "";

            Log.d("Line 1", "* START OF LEGEAL RECEIPT *");
            printStr = details.getData().getStore().getStore_name().toString().toUpperCase();
            Log.d("Line 1", printStr);


            printStr = details.getData().getStore().getAddress().toString().toUpperCase();
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_vat_no) + padLeft(details.getData().getVat_no(), TSIZE22-7);
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_tpin_no) + padLeft(details.getData().getVat_no(), TSIZE22-8);
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_date) + padLeft(DateTimeFormatting.Companion.formatSaleReturndate(details.getData().getPurchase_date_time(), zone, context), TSIZE22-7);
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_receipt_no_short) + padLeft(details.getData().getInvoice_id(), TSIZE22-11);
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_buyer_name) + padLeft(details.getData() != null && details.getData().getCustomer_name() != null ? details.getData().getCustomer_name() : "", TSIZE22-13);
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_buyer_tin) + padLeft(details.getData().getBuyers_tpin(), TSIZE22-13);
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_description);
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_qty_rate_amount);
            Log.d("Line 1", printStr);

            printStr = "----------------------------------";
            Log.d("Line 1", printStr);


            for (SalesItem item : details.getData().getSalesItem()){
                printStr = item.getProduct_name();
                Log.d("Line 1", printStr);

                //String qtyRate = FunUtils.INSTANCE.DtoString(item.getQuantity()) + " X " + FunUtils.INSTANCE.formatPrintPrice(item.getRetail_price());
                String qtyRate = FunUtils.INSTANCE.DtoString(Double.parseDouble(item.getQuantity()))+" (" + item.getUom()+ ") X " + FunUtils.INSTANCE.formatPrintPrice(item.getRetail_price());
                String amount = FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_amount()));
                String Line2 = qtyRate + padLeft(amount, TSIZE22 - (qtyRate.length()));
                printStr = Line2;
                Log.d("Line 1", printStr);
            }

            printStr = "----------------------------------";
            Log.d("Line 1", printStr);


            printStr = "TOTAL (" + currency + ") :" + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getSub_total()), TSIZE26 - (("TOTAL (" + currency + ") :").length()));
            Log.d("Line 1", printStr);


            printStr = context.getString(R.string.receipt_label_items) + padLeft(Integer.toString(details.getData().getSalesItem().size()), TSIZE22 - 6 );
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_tax_ex) + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_ex()), TSIZE22 - 7 );
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_tax_vat) + padLeft(details.getData().getTax()+"%", TSIZE22 - 9 );
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_total_vat) + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_amount()), TSIZE22 - 10 );
            Log.d("Line 1", printStr);
            printStr = "----------------------------------";
            Log.d("Line 1", printStr);
            printStr = context.getString(R.string.receipt_label_payable) + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()), TSIZE26-8);
            Log.d("Line 1", printStr);



            String cashAmt = details.getData().getPayment_type().trim().toUpperCase().equals("CASH") ?
                    FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()) : "";
            printStr = receiptStr(R.string.payment_type_cash) + padLeft(cashAmt, TSIZE20 - 5);
            Log.d("Line 1", printStr);
            String nonCashAmt = details.getData().getPayment_type().trim().toUpperCase().equals("CARD") ||
                    details.getData().getPayment_type().trim().toUpperCase().equals("M-MONEY") ?
                    FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()) : "";
            printStr = context.getString(R.string.receipt_label_mmoney_receipt) + padLeft(nonCashAmt, TSIZE20 - 8);
            Log.d("Line 1", printStr);

            printStr = "----------------------------------";
            Log.d("Line 1", printStr);


            printStr = context.getString(R.string.receipt_label_sign_stamp);
            Log.d("Line 1", printStr);

            printStr = context.getString(R.string.receipt_label_end_of_receipt);
            Log.d("Line 1", printStr);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.toString());

            Result = e.toString();
            if (Result.contains("NoPaperException")) {
                nopaper = true;
            } else if (Result.contains("OverHeatException")) {
                handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
            } else {
                handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
            }
        }finally {
            handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
            if (nopaper) {
                handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                nopaper = false;
                return;
            }
        }

    }*/

    private Bitmap generateQRCodeBitmap(String data, int width, int height) {
        try {
            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, width, height);

            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();

            Bitmap bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888);

            for (int x = 0; x < matrixWidth; x++) {
                for (int y = 0; y < matrixHeight; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ?
                            android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }

            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }



    private void printSaleType(PosSalesDetails details) {
        Log.e("PrinterUtil_Debug", "printSaleType() called");
        com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper orgHelper = new com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper(context);
        String receiptType = orgHelper.getOrganisationData().getReciept_type();
        Log.e("PrinterUtil_Debug", "printSaleType(): retrieved receiptType = '" + receiptType + "'");
        if (receiptType == null || receiptType.trim().isEmpty()) {
            Log.e("PrinterUtil_Debug", "printSaleType(): receiptType is null or empty, defaulting to 'rra'");
            receiptType = "rra";
        }

        if (receiptType.equalsIgnoreCase("rra")) {
            Log.e("PrinterUtil_Debug", "printSaleType(): routing to 'rra' layout");
            try {
                String rcptType = "";
                if (details.getData() != null && details.getData().getRcptType() != null) {
                rcptType = details.getData().getRcptType();
            }else {
                rcptType = "Proforma";
            }
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setLeftIndent(1);
            mUsbThermalPrinter.setLineSpace(3);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setGray(6);

            mUsbThermalPrinter.addString("CIS Version : 1.0.1");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(3);

            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

            // Load logo bitmap
            Bitmap logoBitmap = BitmapFactory.decodeResource(
                    context.getResources(),
                    R.drawable.image22
            );

// Optional: resize logo for printer
            logoBitmap = Bitmap.createScaledBitmap(
                    logoBitmap,
                    400,   // width (safe for 58mm)
                    200,    // height
                    true
            );

            mUsbThermalPrinter.printLogo(logoBitmap, false);
            mUsbThermalPrinter.walkPaper(1);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(32);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(true);

            Log.d("PrinterUtil", "Checking data before printing store info...");
            if (details == null) {
                Log.e("PrinterUtil", "DEBUG: details object is completely NULL!");
            } else if (details.getData() == null) {
                Log.e("PrinterUtil", "DEBUG: details.getData() is NULL!");
            } else if (details.getData().getStore() == null) {
                Log.e("PrinterUtil", "DEBUG: details.getData().getStore() is NULL!");
            } else {
                Log.d("PrinterUtil", "Store Name: " + details.getData().getStore().getStore_name());
            }

            mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(26);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(2);

            
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(24);
            mUsbThermalPrinter.setGray(6);

            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) +details.getData().getTpin_no());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            if (rcptType.equalsIgnoreCase("P" ) || rcptType.equalsIgnoreCase("Proforma")) {
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(details.getData().getRcptType() );
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            } else if (rcptType.equalsIgnoreCase("T")|| rcptType.equalsIgnoreCase("Training")) {
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(details.getData().getRcptType() + " MODE");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(details.getData().getRcptType());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            }
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(6);
            if (rcptType == null){
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            }
            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_welcome));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

            String buyerName = details.getData() != null && details.getData().getCustomer_name() != null
                    ? details.getData().getCustomer_name()
                    : "N/A";

            // â”€â”€ CHANGED: Buyer info with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
            mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.setGray(6);

            String buyerTin = details.getData() != null && details.getData().getBuyers_tpin() != null
                    ? details.getData().getBuyers_tpin()
                    : "N/A";
            String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
            mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
            mUsbThermalPrinter.printString();

            String buyerContact = details.getData() != null && details.getData().getCustomer_mob_no() != null
                    ? details.getData().getCustomer_mob_no()
                    : "N/A";
            String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
            mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(22);

            mUsbThermalPrinter.setGray(6);

            mUsbThermalPrinter.setGray(6);

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.setGray(6);

            for (SalesItem item : details.getData().getSalesItem()) {
                productname = item.getProduct_name();
                numberOfItems++;
                if (productname.toLowerCase().startsWith("bulk oil")) {
                    looseOil = true;
                } else {
                    looseOil = false;
                }

                // Line 1: Product Name (bold)
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(item.getProduct_name());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);

                // --- Build 3-column line: [rate x]   [qty]   [amount taxCode] ---
                String taxCode = "";
                if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                    taxCode = item.getTax_details().getCode();
                }

                // Left: "33600.00x"
                String rateCol = FunUtils.INSTANCE.formatPrintPrice(item.getTax_inclusive_price()) + "x";

                // Middle: "0.200 (KG)"  or just quantity + UOM
                String qtyCol = FunUtils.INSTANCE.DtoString(Double.parseDouble(item.getQuantity()));

                // Right: "6720.00B"
                String amountCol = FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_amount())) + taxCode;

                // Total line width for textSize 22 (34 chars for 58mm printer)
                int totalWidth = TSIZE22; // e.g. 34

                // Right column occupies fixed 10 chars from right
                int rightColWidth = 10;
                // Middle column occupies fixed 6 chars
                int midColWidth = 8;
                // Left col gets remaining
                int leftColWidth = totalWidth - midColWidth - rightColWidth;

                // Format: left-pad qty to midColWidth, right-pad amount to rightColWidth
                String line2 = String.format(
                        "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                        rateCol,
                        qtyCol,
                        amountCol
                );

                mUsbThermalPrinter.addString(line2);
                mUsbThermalPrinter.printString();

                // Discount line (if applicable)
                if (item.getDiscount_rate() < 0) {
                    String discountText = "discount " + item.getDiscount_rate() + "%";

                    double discountedTotal = item.getTotal_amount()
                            + (item.getTotal_amount() * item.getDiscount_rate() / 100);

                    String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(discountedTotal));

                    // Left: "discount -25%"   Right: "5040.00" right-aligned
                    String discountLine = String.format(
                            "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                            discountText,
                            discountAmountStr
                    );

                    mUsbThermalPrinter.addString(discountLine);
                    mUsbThermalPrinter.printString();
                }

                mUsbThermalPrinter.walkPaper(1);
            }



            mUsbThermalPrinter.setTextSize(22);
            String spotAmount = details.getData().getSpot_discount_amount();
            if (hasSpotDiscount(spotAmount)) {
                String pct = details.getData().getSpot_discount_percentage();
                String pctVal = (pct != null && !pct.isEmpty()) ? pct : "0";
                String sptdiscount = receiptStr(R.string.print_on_spot_discount_label, pctVal);
                mUsbThermalPrinter.addString(sptdiscount + padLeft(FunUtils.INSTANCE.formatPrintPrice(spotAmount), TSIZE22 - sptdiscount.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            } else {
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            }
            // âœ… Check by receipt type code
            if (rcptType.equalsIgnoreCase("P") ||
                    rcptType.equalsIgnoreCase("T") ||
                    rcptType.equalsIgnoreCase("C") ||
                    rcptType.equalsIgnoreCase("Proforma") ||
                    rcptType.equalsIgnoreCase("Training") ||
                    rcptType.equalsIgnoreCase("Copy")) {

                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            }

            mUsbThermalPrinter.setTextSize(26);
            mUsbThermalPrinter.setBold(true);
            String label = "TOTAL(" + currency + "):";
            String value = FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total());
            // Right align the value using padLeft correctly
            int padding = TSIZE26 - label.length();
            mUsbThermalPrinter.addString(label + padLeft(value, padding));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.walkPaper(2);
            mUsbThermalPrinter.setTextSize(22);

            // Get tax summary list
            List<TaxSummary> taxSummaryList = details.getData().getTax_summery();

            if (taxSummaryList != null && !taxSummaryList.isEmpty()) {

                for (TaxSummary taxSummary : taxSummaryList) {
                    String code = taxSummary.getCode();
                    Double taxableValue = taxSummary.getTaxable_value();

                    if (code != null ) {
                        String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                        mUsbThermalPrinter.addString(taxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                TSIZE22 - taxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                for (TaxSummary taxSummary : taxSummaryList) {
                    String code = taxSummary.getCode();
                    Double taxAmount = taxSummary.getTax_amount();

                    if (taxAmount != null && taxAmount > 0) {
                        String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                        mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmount)),
                                TSIZE22 - taxAmountLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                String totalTaxAmount = details.getData().getTax_amount();
                if (totalTaxAmount != null && !totalTaxAmount.equals("0")) {
                    String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                    mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                            FunUtils.INSTANCE.formatPrintPrice(totalTaxAmount),
                            TSIZE22 - totalTaxLabel.length()
                    ));
                    mUsbThermalPrinter.printString();
                }
            }
            mUsbThermalPrinter.walkPaper(1);



            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(20);
            // â”€â”€ Payment / Items count (skip for Proforma) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (!rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                String paymentType = details.getData().getPayment_type().trim();
                String grandTotal = FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total());

                String paymentLabel = "";
                if (paymentType.equals("01"))      paymentLabel = receiptStr(R.string.payment_type_cash);
                else if (paymentType.equals("02")) paymentLabel = receiptStr(R.string.payment_type_credit);
                else if (paymentType.equals("03")) paymentLabel = receiptStr(R.string.payment_type_cash_credit);
                else if (paymentType.equals("04")) paymentLabel = receiptStr(R.string.payment_type_bank_check);
                else if (paymentType.equals("05")) paymentLabel = receiptStr(R.string.payment_type_card);
                else if (paymentType.equals("06")) paymentLabel = receiptStr(R.string.payment_type_m_money);
                else if (paymentType.equals("07")) paymentLabel = receiptStr(R.string.payment_type_other);
                else if (paymentType.equalsIgnoreCase("CASH")) paymentLabel = receiptStr(R.string.payment_type_cash);
                else if (paymentType.equalsIgnoreCase("CARD")) paymentLabel = receiptStr(R.string.payment_type_card);
                else if (paymentType.equalsIgnoreCase("M-MONEY")) paymentLabel = receiptStr(R.string.payment_type_m_money);

                if (!paymentLabel.isEmpty()) {
                    mUsbThermalPrinter.addString(paymentLabel + padLeft(grandTotal, TSIZE20 - paymentLabel.length()));
                    mUsbThermalPrinter.printString();
                }

                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(Integer.toString(details.getData().getSalesItem().size()), TSIZE22 - 6));
                mUsbThermalPrinter.printString();
            } else {
                mUsbThermalPrinter.walkPaper(1); // maintain spacing for Proforma
            }


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            if (rcptType.equalsIgnoreCase("P" ) || rcptType.equalsIgnoreCase("Proforma")) {
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.addString(details.getData().getRcptType() );
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            } else if (rcptType.equalsIgnoreCase("T")|| rcptType.equalsIgnoreCase("Training")) {
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.addString(details.getData().getRcptType() + " Mode");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.addString(details.getData().getRcptType());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
            }
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(6);
            VsdcReceipt vsdc = (details.getData().getVsdc_reciept() != null && !details.getData().getVsdc_reciept().isEmpty())
                    ? details.getData().getVsdc_reciept().get(0) : null;

            String rawDateTime = (vsdc != null) ? vsdc.getVsdcRcptPbctDate() : "";

            String vsdcDate = "";
            String vsdcTime = "";
            if (rawDateTime != null && !rawDateTime.isEmpty()) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                    Date parsedDate = inputFormat.parse(rawDateTime);
                    if (parsedDate != null) {
                        vsdcDate = formatReceiptDate(parsedDate);
                        vsdcTime = new SimpleDateFormat("HH:mm:ss",    Locale.getDefault()).format(parsedDate);
                    }
                } catch (ParseException e) {
                    Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                    vsdcDate = rawDateTime; // fallback
                }
            }

            String dateLabel = receiptStr(R.string.receipt_label_date) + " " + vsdcDate;
            String timeLabel = receiptStr(R.string.receipt_label_time) + " " + vsdcTime;
            int spaces = TSIZE22 - dateLabel.length() - timeLabel.length();
            if (spaces < 1) spaces = 1;
            StringBuilder dateLine = new StringBuilder(dateLabel);
            for (int i = 0; i < spaces; i++) dateLine.append(" ");
            dateLine.append(timeLabel);

            mUsbThermalPrinter.addString(dateLine.toString());
            mUsbThermalPrinter.printString();

            // â”€â”€ CHANGED: SDC ID with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
            String sdcIdValue = (vsdc != null && vsdc.getSdcId() != null)
                    ? vsdc.getSdcId() : "";
            mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcIdValue, TSIZE22 - sdcIdLabel.length()));
            mUsbThermalPrinter.printString();

            String rcptTypeValue = details.getData().getRcptType() != null ? details.getData().getRcptType() : "N";
            String receipttype = (vsdc != null ? vsdc.getSales_type() : "") + "" + "S";

            // â”€â”€ CHANGED: Receipt Number (SDC section) with padLeft â”€â”€â”€
            String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
            String sdcRcptValue = (vsdc != null ? vsdc.getTotRcptNo() : "")
                    + "/" + (vsdc != null ? vsdc.getTotRcptNo() : "")
                    + " " + receipttype;
            mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.setAlgin(1);
            if (!rcptType.equalsIgnoreCase("T") && !rcptType.equalsIgnoreCase("Training") &&
                    !rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + (vsdc != null ? vsdc.getIntrlData() : ""));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + (vsdc != null ? vsdc.getRcptSign() : ""));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            }

// GENERATE AND PRINT QR CODE HERE â¬‡ï¸
            if (!rcptType.equalsIgnoreCase("T") && !rcptType.equalsIgnoreCase("Training") &&
                    !rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                try {
                    if (vsdc != null &&
                            vsdc.getQrCodeUrl() != null &&
                            !vsdc.getQrCodeUrl().isEmpty()) {

                        mUsbThermalPrinter.reset();
                        mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                        String qrData = vsdc.getQrCodeUrl();
                        Bitmap qrBitmap = generateQRCodeBitmap(qrData, 250, 250);

                        if (qrBitmap != null) {
                            try {
                                mUsbThermalPrinter.setGray(6);
                                mUsbThermalPrinter.printLogo(qrBitmap, false);
                                mUsbThermalPrinter.walkPaper(2);
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.e("PrinterUtil", "Error printing QR code: " + e.getMessage());
                            }
                        } else {
                            Log.w("PrinterUtil", "QR code bitmap is null");
                        }
                    } else {
                        Log.w("PrinterUtil", "No QR code URL available - skipping QR code");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("PrinterUtil", "Exception in QR code section: " + e.getMessage());
                }
            } // END QR code condition

            mUsbThermalPrinter.setAlgin(0);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ CHANGED: Receipt Number (after QR) with padLeft â”€â”€â”€â”€â”€â”€


            String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
            String receiptNum = (details.getData().getInvoice_id() != null)
                    ? details.getData().getInvoice_id() : "";
            mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
            mUsbThermalPrinter.printString();


            String rawDateTimee = details.getData().getPurchase_date_time();

            String vsdcDate2 = "";
            String vsdcTime2 = "";
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                Date parsedDate = inputFormat.parse(rawDateTimee);
                if (parsedDate != null) {
                    vsdcDate2 = formatReceiptDate(parsedDate);
                    vsdcTime2 = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedDate);
                }
            } catch (ParseException e) {
                try {
                    SimpleDateFormat fallbackFormat = new SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault());
                    Date parsedDate = fallbackFormat.parse(rawDateTimee);
                    if (parsedDate != null) {
                        vsdcDate2 = formatReceiptDate(parsedDate);
                        vsdcTime2 = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedDate);
                    }
                } catch (ParseException e2) {
                    Log.e("DateFormat", "Parsing failed: " + e2.getMessage());
                    vsdcDate2 = rawDateTimee; // fallback
                }
            }

            String dateLabel2 = "DATE: " + vsdcDate2;
            String timeLabel2 = "TIME: " + vsdcTime2;
            int spaces2 = TSIZE22 - dateLabel2.length() - timeLabel2.length();
            if (spaces2 < 1) spaces2 = 1;
            StringBuilder dateLine2 = new StringBuilder(dateLabel2);
            for (int i = 0; i < spaces2; i++) dateLine2.append(" ");
            dateLine2.append(timeLabel2);

            mUsbThermalPrinter.addString(dateLine2.toString());
            mUsbThermalPrinter.printString();

            // â”€â”€ CHANGED: MRC No with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
            String mrcValue = (vsdc != null && vsdc.getMrcNo() != null)
                    ? vsdc.getMrcNo() + "." : ".";
            mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.setAlgin(0);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();

// âœ… IMPORTANT: Reset printer state after QR code attempt
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setGray(6);

// Now print THANK YOU
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_appreciate_biz));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setLineSpace(3);
            mUsbThermalPrinter.setTextSize(20);

            mUsbThermalPrinter.addString("*** END ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(5);
            mUsbThermalPrinter.addString(" ");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(5);
            mUsbThermalPrinter.reset();

        } catch (Exception e) {
            Log.e("PrinterUtil_Debug", "printSaleType(rra): Exception caught during printing", e);
            e.printStackTrace();
            System.out.println(e.toString());
            Log.e("PrinterUtil", "CRASH in printSaleType: " + e.getMessage(), e);

            Result = e.toString();
            if (Result.contains("NoPaperException")) {
                Log.e("PrinterUtil_Debug", "printSaleType(rra): NoPaperException detected!");
                nopaper = true;
            } else if (Result.contains("OverHeatException")) {
                Log.e("PrinterUtil_Debug", "printSaleType(rra): OverHeatException detected!");
                handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
            } else {
                Log.e("PrinterUtil_Debug", "printSaleType(rra): General PRINTERR detected!");
                handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
            }
        } finally {
            Log.e("PrinterUtil_Debug", "printSaleType(rra): finally block reached");
            handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
            if (nopaper) {
                Log.e("PrinterUtil_Debug", "printSaleType(rra): nopaper flag is true, notifying handler");
                handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                nopaper = false;
                return;
            }
        }
        } else if (receiptType.equalsIgnoreCase("moz")) {

                try {
                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setLeftIndent(1);
                    mUsbThermalPrinter.setLineSpace(3);
                    mUsbThermalPrinter.setTextSize(20);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("*** Internal Invoice ***");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(3);

                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(32);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(true);

                    mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(26);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(2);


                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                    mUsbThermalPrinter.setTextSize(22);


//            mUsbThermalPrinter.setGray(6);
//            mUsbThermalPrinter.addString(context.getString(R.string.receipt_label_vat_no) + padLeft(details.getData().getVat_no(), TSIZE22-7));
//            mUsbThermalPrinter.printString();
                    String tinLabelStr = context.getString(R.string.receipt_label_tin_no);
                    mUsbThermalPrinter.addString(tinLabelStr + padLeft(details.getData().getTpin_no(), TSIZE22 - tinLabelStr.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(2);
                    String dateLabelStr = context.getString(R.string.receipt_label_date);
                    mUsbThermalPrinter.addString(dateLabelStr + padLeft(DateTimeFormatting.Companion.formatSaleReturndate(details.getData().getPurchase_date_time(), zone, context), TSIZE22 - dateLabelStr.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(2);

                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setGray(6);
                    String sdcLabel = context.getString(R.string.receipt_label_sdc_receipt_no);
                    String sdcValue = details.getData().getInvoice_id() != null && !details.getData().getInvoice_id().isEmpty()
                            ? details.getData().getInvoice_id() : "N/A";
                    mUsbThermalPrinter.addString(sdcLabel + padLeft(sdcValue, TSIZE22 - sdcLabel.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setGray(6);
                    String nameLabel = context.getString(R.string.receipt_label_buyer_name);
                    mUsbThermalPrinter.addString(nameLabel + padLeft(
                            details.getData() != null && details.getData().getCustomer_name() != null && !details.getData().getCustomer_name().isEmpty()
                                    ? details.getData().getCustomer_name() : "N/A",
                            TSIZE22 - nameLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setGray(6);

                    String tinLabel = context.getString(R.string.receipt_label_buyer_tin);
                    mUsbThermalPrinter.addString(tinLabel + padLeft(
                            details.getData().getBuyers_tpin() != null && !details.getData().getBuyers_tpin().isEmpty()
                                    ? details.getData().getBuyers_tpin() : "N/A",
                            TSIZE22 - tinLabel.length()));
                    mUsbThermalPrinter.printString();

                    String mobLabel =context.getString(R.string.receipt_label_buyer_mobile);
                    mUsbThermalPrinter.addString(mobLabel + padLeft(
                            details.getData().getCustomer_mob_no() != null && !details.getData().getCustomer_mob_no().isEmpty()
                                    ? details.getData().getCustomer_mob_no() : "N/A",
                            TSIZE22 - mobLabel.length()));
                    mUsbThermalPrinter.printString();

                    String vatLabel = receiptStr(R.string.receipt_label_buyer_iva_no);
                    mUsbThermalPrinter.addString(vatLabel + padLeft(
                            details.getData().getVat_no() != null && !details.getData().getVat_no().isEmpty()
                                    ? details.getData().getVat_no() : "N/A",
                            TSIZE22 - vatLabel.length()));
                    mUsbThermalPrinter.printString();




//                    if (details.getData() != null
//                            && details.getData().getCustomer_address() != null
//                            && !details.getData().getCustomer_address().isEmpty()) {
//
//                        String addressLabel = context.getString(R.string.print_buyers_address_label);
//                        String addressValue = details.getData().getCustomer_address();
//
//                        // Check if label + address fits within the line width
//                        if ((addressLabel.length() + addressValue.length()) <= TSIZE22) {
//                            // Short address — print inline on same line
//                            mUsbThermalPrinter.addString(
//                                    addressLabel + padLeft(addressValue, TSIZE22 - addressLabel.length())
//                            );
//                            mUsbThermalPrinter.printString();
//                        } else {
//                            // Long address — label on first line, address on next line centered
//                            mUsbThermalPrinter.addString(addressLabel);
//                            mUsbThermalPrinter.printString();
//
//                            mUsbThermalPrinter.setAlgin(1);
//                            mUsbThermalPrinter.addString(addressValue);
//                            mUsbThermalPrinter.printString();
//                            mUsbThermalPrinter.setAlgin(0);
//                        }
//
//                    } else {
//
//                        String addressLabel = context.getString(R.string.print_buyers_address_label);
//                        mUsbThermalPrinter.addString(addressLabel + padLeft("N/A", TSIZE22 - addressLabel.length()));
//                        mUsbThermalPrinter.printString();
//
//                    }




                    mUsbThermalPrinter.walkPaper(2);

                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_description));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_qty_rate_amount));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    for (SalesItem item : details.getData().getSalesItem()){
                        productname = item.getProduct_name();
                        numberOfItems++;
                        if (productname.toLowerCase().startsWith("bulk oil") ){
                            looseOil = true;
                        }else {
                            looseOil = false;
                        }
                        mUsbThermalPrinter.setTextSize(24);
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.addString(item.getProduct_name());
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setBold(false);

                        mUsbThermalPrinter.setTextSize(22);
                        String qtyRate = FunUtils.INSTANCE.DtoString(Double.parseDouble(item.getQuantity())) + " (" + item.getUom()+ ") X " + FunUtils.INSTANCE.formatPrintPrice(item.getRetail_price());
                        String amount = FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_amount()));
                        String Line2 = qtyRate + padLeft(amount, TSIZE22 - (qtyRate.length()));
                        mUsbThermalPrinter.addString(Line2);
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    }
                    mUsbThermalPrinter.setTextSize(22);
                    String spot = details.getData().getSpot_discount_amount();
                    if (hasSpotDiscount(spot)){
                        String pct = details.getData().getSpot_discount_percentage();
                        String pctVal = (pct != null && !pct.isEmpty()) ? pct : "0";
                String sptdiscount = receiptStr(R.string.print_on_spot_discount_label, pctVal);
                        mUsbThermalPrinter.addString(sptdiscount + padLeft(FunUtils.INSTANCE.formatPrintPrice(spot), TSIZE22 - sptdiscount.length()));
                        mUsbThermalPrinter.printString();
                    }else {
                        mUsbThermalPrinter.walkPaper(1);
                    }
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.setTextSize(26);
                    mUsbThermalPrinter.setBold(true);
                    String totalLabelStr = "TOTAL(" + currency + "):";
                    mUsbThermalPrinter.addString(totalLabelStr + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getSub_total()), TSIZE26 - totalLabelStr.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.walkPaper(2);


                    mUsbThermalPrinter.setTextSize(22);
                    String itemsLabelStr = context.getString(R.string.receipt_label_items);
                    mUsbThermalPrinter.addString(itemsLabelStr + padLeft(Integer.toString(details.getData().getSalesItem().size()), TSIZE22 - itemsLabelStr.length()));
                    mUsbThermalPrinter.printString();
                    String ivaExLabel = receiptStr(R.string.print_iva_ex_label);
                    mUsbThermalPrinter.addString(ivaExLabel + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_ex()), TSIZE22 - ivaExLabel.length()));
                    mUsbThermalPrinter.printString();
                    String ivaVatLabel = receiptStr(R.string.print_iva_vat_label);
                    if (looseOil && numberOfItems == 1){
                        mUsbThermalPrinter.addString(
                                ivaVatLabel + padLeft("INC. 16%", TSIZE22 - ivaVatLabel.length()));
                    }else {
                        mUsbThermalPrinter.addString(ivaVatLabel + padLeft(details.getData().getTax()+"%", TSIZE22 - ivaVatLabel.length() ));
                    }
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(true);
                    String totalIvaLabel = receiptStr(R.string.print_total_iva_label);
                    mUsbThermalPrinter.addString(totalIvaLabel + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_amount()), TSIZE22 - totalIvaLabel.length() ));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setTextSize(26);
                    String payableLabelStr = context.getString(R.string.receipt_label_payable);
                    mUsbThermalPrinter.addString(payableLabelStr + padLeft(FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()), TSIZE26 - payableLabelStr.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(20);
                    String cashAmt = details.getData().getPayment_type().trim().toUpperCase().equals("CASH") ?
                            FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()) : "";
                    String cashLabelStr = receiptStr(R.string.payment_type_cash);
                    mUsbThermalPrinter.addString(cashLabelStr + padLeft(cashAmt, TSIZE20 - cashLabelStr.length()));
                    mUsbThermalPrinter.printString();
                    String nonCashAmt = details.getData().getPayment_type().trim().toUpperCase().equals("CARD") ||
                            details.getData().getPayment_type().trim().toUpperCase().equals("M-MONEY") ?
                            FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()) : "";
                    String mMoneyLabelStr = receiptStr(R.string.payment_type_m_money);
                    mUsbThermalPrinter.addString(mMoneyLabelStr + padLeft(nonCashAmt, TSIZE20 - mMoneyLabelStr.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(2);

//            mUsbThermalPrinter.setTextSize(24);
//            mUsbThermalPrinter.addString("Sign/Stamp_____________________");
//            mUsbThermalPrinter.printString();
//            mUsbThermalPrinter.walkPaper(4);

                    mUsbThermalPrinter.setLeftIndent(1);
                    mUsbThermalPrinter.setLineSpace(3);
                    mUsbThermalPrinter.setTextSize(20);
                    mUsbThermalPrinter.addString("*** END ***");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(15);
                    mUsbThermalPrinter.reset();

                } catch (Exception e) {
                    Log.e("PrinterUtil_Debug", "printSaleType(moz): Exception caught during printing", e);
                    e.printStackTrace();
                    System.out.println(e.toString());

                    Result = e.toString();
                    if (Result.contains("NoPaperException")) {
                        Log.e("PrinterUtil_Debug", "printSaleType(moz): NoPaperException detected!");
                        nopaper = true;
                    } else if (Result.contains("OverHeatException")) {
                        Log.e("PrinterUtil_Debug", "printSaleType(moz): OverHeatException detected!");
                        handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                    } else {
                        Log.e("PrinterUtil_Debug", "printSaleType(moz): General PRINTERR detected!");
                        handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                    }
                }finally {
                    Log.e("PrinterUtil_Debug", "printSaleType(moz): finally block reached");
                    handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                    if (nopaper) {
                        Log.e("PrinterUtil_Debug", "printSaleType(moz): nopaper flag is true, notifying handler");
                        handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                        nopaper = false;
                        return;
                    }
                }


        } else if (receiptType.equalsIgnoreCase("default")) {
            try {
                String rcptType = "";
                if (details.getData() != null && details.getData().getRcptType() != null) {
                    rcptType = details.getData().getRcptType();
                }else {
                    rcptType = "Proforma";
                }
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setLeftIndent(1);
                mUsbThermalPrinter.setLineSpace(3);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setGray(6);

                mUsbThermalPrinter.addString("CIS Version : 1.0.1");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                // Load logo bitmap
                Bitmap logoBitmap = BitmapFactory.decodeResource(
                        context.getResources(),
                        R.drawable.image22
                );

// Optional: resize logo for printer
                logoBitmap = Bitmap.createScaledBitmap(
                        logoBitmap,
                        400,   // width (safe for 58mm)
                        200,    // height
                        true
                );

//                mUsbThermalPrinter.printLogo(logoBitmap, false);
//                mUsbThermalPrinter.walkPaper(1);


                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(32);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);

                Log.d("PrinterUtil", "Checking data before printing store info...");
                if (details == null) {
                    Log.e("PrinterUtil", "DEBUG: details object is completely NULL!");
                } else if (details.getData() == null) {
                    Log.e("PrinterUtil", "DEBUG: details.getData() is NULL!");
                } else if (details.getData().getStore() == null) {
                    Log.e("PrinterUtil", "DEBUG: details.getData().getStore() is NULL!");
                } else {
                    Log.d("PrinterUtil", "Store Name: " + details.getData().getStore().getStore_name());
                }

                mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(2);


                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setGray(6);

                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) +details.getData().getTpin_no());
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                if (rcptType.equalsIgnoreCase("P" ) || rcptType.equalsIgnoreCase("Proforma")) {
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(details.getData().getRcptType() );
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                } else if (rcptType.equalsIgnoreCase("T")|| rcptType.equalsIgnoreCase("Training")) {
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(details.getData().getRcptType() + " MODE");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(details.getData().getRcptType());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                }
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setGray(6);
                if (rcptType == null){
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                }
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_welcome));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

                String buyerName = details.getData() != null && details.getData().getCustomer_name() != null
                        ? details.getData().getCustomer_name()
                        : "N/A";

                // â”€â”€ CHANGED: Buyer info with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
                mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setGray(6);

                String buyerTin = details.getData() != null && details.getData().getBuyers_tpin() != null
                        ? details.getData().getBuyers_tpin()
                        : "N/A";
                String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
                mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerContact = details.getData() != null && details.getData().getCustomer_mob_no() != null
                        ? details.getData().getCustomer_mob_no()
                        : "N/A";
                String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
                mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.setTextSize(22);

                mUsbThermalPrinter.setGray(6);

                mUsbThermalPrinter.setGray(6);

                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setGray(6);

                for (SalesItem item : details.getData().getSalesItem()) {
                    productname = item.getProduct_name();
                    numberOfItems++;
                    if (productname.toLowerCase().startsWith("bulk oil")) {
                        looseOil = true;
                    } else {
                        looseOil = false;
                    }

                    // Line 1: Product Name (bold)
                    mUsbThermalPrinter.setTextSize(24);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.addString(item.getProduct_name());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);

                    // --- Build 3-column line: [rate x]   [qty]   [amount taxCode] ---
                    String taxCode = "";
                    if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                        taxCode = item.getTax_details().getCode();
                    }

                    // Left: "33600.00x"
                    String rateCol = FunUtils.INSTANCE.formatPrintPrice(item.getTax_inclusive_price()) + "x";

                    // Middle: "0.200 (KG)"  or just quantity + UOM
                    String qtyCol = FunUtils.INSTANCE.DtoString(Double.parseDouble(item.getQuantity()));

                    // Right: "6720.00B"
                    String amountCol = FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_amount())) + taxCode;

                    // Total line width for textSize 22 (34 chars for 58mm printer)
                    int totalWidth = TSIZE22; // e.g. 34

                    // Right column occupies fixed 10 chars from right
                    int rightColWidth = 10;
                    // Middle column occupies fixed 6 chars
                    int midColWidth = 8;
                    // Left col gets remaining
                    int leftColWidth = totalWidth - midColWidth - rightColWidth;

                    // Format: left-pad qty to midColWidth, right-pad amount to rightColWidth
                    String line2 = String.format(
                            "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                            rateCol,
                            qtyCol,
                            amountCol
                    );

                    mUsbThermalPrinter.addString(line2);
                    mUsbThermalPrinter.printString();

                    // Discount line (if applicable)
                    if (item.getDiscount_rate() < 0) {
                        String discountText = "discount " + item.getDiscount_rate() + "%";

                        double discountedTotal = item.getTotal_amount()
                                + (item.getTotal_amount() * item.getDiscount_rate() / 100);

                        String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(discountedTotal));

                        // Left: "discount -25%"   Right: "5040.00" right-aligned
                        String discountLine = String.format(
                                "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                discountText,
                                discountAmountStr
                        );

                        mUsbThermalPrinter.addString(discountLine);
                        mUsbThermalPrinter.printString();
                    }

                    mUsbThermalPrinter.walkPaper(1);
                }



                mUsbThermalPrinter.setTextSize(22);
                String spot = details.getData().getSpot_discount_amount();
                if (hasSpotDiscount(spot)){
                    String pct = details.getData().getSpot_discount_percentage();
                    String pctVal = (pct != null && !pct.isEmpty()) ? pct : "0";
                String sptdiscount = receiptStr(R.string.print_on_spot_discount_label, pctVal);
                    mUsbThermalPrinter.addString(sptdiscount + padLeft(FunUtils.INSTANCE.formatPrintPrice(spot), TSIZE22 - sptdiscount.length()));
                    mUsbThermalPrinter.printString();
                }else {
                    mUsbThermalPrinter.walkPaper(1);
                }
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                // âœ… Check by receipt type code
                if (rcptType.equalsIgnoreCase("P") ||
                        rcptType.equalsIgnoreCase("T") ||
                        rcptType.equalsIgnoreCase("C") ||
                        rcptType.equalsIgnoreCase("Proforma") ||
                        rcptType.equalsIgnoreCase("Training") ||
                        rcptType.equalsIgnoreCase("Copy")) {

                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                }

                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setBold(true);
                String label = "TOTAL(" + currency + "):";
                String value = FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total());
                // Right align the value using padLeft correctly
                int padding = TSIZE26 - label.length();
                mUsbThermalPrinter.addString(label + padLeft(value, padding));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.walkPaper(2);
                mUsbThermalPrinter.setTextSize(22);

                // Get tax summary list
                List<TaxSummary> taxSummaryList = details.getData().getTax_summery();

                if (taxSummaryList != null && !taxSummaryList.isEmpty()) {

                    for (TaxSummary taxSummary : taxSummaryList) {
                        String code = taxSummary.getCode();
                        Double taxableValue = taxSummary.getTaxable_value();

                        if (code != null ) {
                            String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                            mUsbThermalPrinter.addString(taxLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                    TSIZE22 - taxLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    for (TaxSummary taxSummary : taxSummaryList) {
                        String code = taxSummary.getCode();
                        Double taxAmount = taxSummary.getTax_amount();

                        if (taxAmount != null && taxAmount > 0) {
                            String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                            mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmount)),
                                    TSIZE22 - taxAmountLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    String totalTaxAmount = details.getData().getTax_amount();
                    if (totalTaxAmount != null && !totalTaxAmount.equals("0")) {
                        String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                        mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(totalTaxAmount),
                                TSIZE22 - totalTaxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }
                mUsbThermalPrinter.walkPaper(1);



                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(20);
                // â”€â”€ Payment / Items count (skip for Proforma) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                if (!rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    String paymentType = details.getData().getPayment_type().trim();
                    String grandTotal = FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total());

                    String paymentLabel = "";
                    if (paymentType.equals("01"))      paymentLabel = receiptStr(R.string.payment_type_cash);
                    else if (paymentType.equals("02")) paymentLabel = receiptStr(R.string.payment_type_credit);
                    else if (paymentType.equals("03")) paymentLabel = receiptStr(R.string.payment_type_cash_credit);
                    else if (paymentType.equals("04")) paymentLabel = receiptStr(R.string.payment_type_bank_check);
                    else if (paymentType.equals("05")) paymentLabel = receiptStr(R.string.payment_type_card);
                    else if (paymentType.equals("06")) paymentLabel = receiptStr(R.string.payment_type_m_money);
                    else if (paymentType.equals("07")) paymentLabel = receiptStr(R.string.payment_type_other);
                    else if (paymentType.equalsIgnoreCase("CASH")) paymentLabel = receiptStr(R.string.payment_type_cash);
                    else if (paymentType.equalsIgnoreCase("CARD")) paymentLabel = receiptStr(R.string.payment_type_card);
                    else if (paymentType.equalsIgnoreCase("M-MONEY")) paymentLabel = receiptStr(R.string.payment_type_m_money);

                    if (!paymentLabel.isEmpty()) {
                        mUsbThermalPrinter.addString(paymentLabel + padLeft(grandTotal, TSIZE20 - paymentLabel.length()));
                        mUsbThermalPrinter.printString();
                    }

                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(Integer.toString(details.getData().getSalesItem().size()), TSIZE22 - 6));
                    mUsbThermalPrinter.printString();
                } else {
                    mUsbThermalPrinter.walkPaper(1); // maintain spacing for Proforma
                }


                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                if (rcptType.equalsIgnoreCase("P" ) || rcptType.equalsIgnoreCase("Proforma")) {
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.addString(details.getData().getRcptType() );
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                } else if (rcptType.equalsIgnoreCase("T")|| rcptType.equalsIgnoreCase("Training")) {
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.addString(details.getData().getRcptType() + " Mode");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setAlgin(1);
                    mUsbThermalPrinter.addString(details.getData().getRcptType());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                }
//                mUsbThermalPrinter.setBold(false);
//                mUsbThermalPrinter.setTextSize(22);
//                mUsbThermalPrinter.setGray(6);
//                VsdcReceipt vsdc = (details.getData().getVsdc_reciept() != null && !details.getData().getVsdc_reciept().isEmpty())
//                        ? details.getData().getVsdc_reciept().get(0) : null;
//
//                String rawDateTime = (vsdc != null) ? vsdc.getVsdcRcptPbctDate() : "";
//
//                String vsdcDate = "";
//                String vsdcTime = "";
//                if (rawDateTime != null && !rawDateTime.isEmpty()) {
//                    try {
//                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
//                        Date parsedDate = inputFormat.parse(rawDateTime);
//                        if (parsedDate != null) {
//                            vsdcDate = formatReceiptDate(parsedDate);
//                            vsdcTime = new SimpleDateFormat("HH:mm:ss",    Locale.getDefault()).format(parsedDate);
//                        }
//                    } catch (ParseException e) {
//                        Log.e("DateFormat", "Parsing failed: " + e.getMessage());
//                        vsdcDate = rawDateTime; // fallback
//                    }
//                }
//
//                String dateLabel = receiptStr(R.string.receipt_label_date) + " " + vsdcDate;
//                String timeLabel = receiptStr(R.string.receipt_label_time) + " " + vsdcTime;
//                int spaces = TSIZE22 - dateLabel.length() - timeLabel.length();
//                if (spaces < 1) spaces = 1;
//                StringBuilder dateLine = new StringBuilder(dateLabel);
//                for (int i = 0; i < spaces; i++) dateLine.append(" ");
//                dateLine.append(timeLabel);
//
//                mUsbThermalPrinter.addString(dateLine.toString());
//                mUsbThermalPrinter.printString();
//
//                // â”€â”€ CHANGED: SDC ID with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//                String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
//                String sdcIdValue = (vsdc != null && vsdc.getSdcId() != null)
//                        ? vsdc.getSdcId() : "";
//                mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcIdValue, TSIZE22 - sdcIdLabel.length()));
//                mUsbThermalPrinter.printString();
//
//                String rcptTypeValue = details.getData().getRcptType() != null ? details.getData().getRcptType() : "N";
//                String receipttype = (vsdc != null ? vsdc.getSales_type() : "") + "" + "S";
//
//                // â”€â”€ CHANGED: Receipt Number (SDC section) with padLeft â”€â”€â”€
//                String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
//                String sdcRcptValue = (vsdc != null ? vsdc.getTotRcptNo() : "")
//                        + "/" + (vsdc != null ? vsdc.getTotRcptNo() : "")
//                        + " " + receipttype;
//                mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
//                mUsbThermalPrinter.printString();
//
//                mUsbThermalPrinter.setAlgin(1);
//                if (!rcptType.equalsIgnoreCase("T") && !rcptType.equalsIgnoreCase("Training") &&
//                        !rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
//                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + (vsdc != null ? vsdc.getIntrlData() : ""));
//                    mUsbThermalPrinter.printString();
//                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + (vsdc != null ? vsdc.getRcptSign() : ""));
//                    mUsbThermalPrinter.printString();
//                    mUsbThermalPrinter.walkPaper(1);
//                }

// GENERATE AND PRINT QR CODE HERE â¬‡ï¸
//                if (!rcptType.equalsIgnoreCase("T") && !rcptType.equalsIgnoreCase("Training") &&
//                        !rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
//                    try {
//                        if (vsdc != null &&
//                                vsdc.getQrCodeUrl() != null &&
//                                !vsdc.getQrCodeUrl().isEmpty()) {
//
//                            mUsbThermalPrinter.reset();
//                            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
//
//                            String qrData = vsdc.getQrCodeUrl();
//                            Bitmap qrBitmap = generateQRCodeBitmap(qrData, 250, 250);
//
//                            if (qrBitmap != null) {
//                                try {
//                                    mUsbThermalPrinter.setGray(6);
//                                    mUsbThermalPrinter.printLogo(qrBitmap, false);
//                                    mUsbThermalPrinter.walkPaper(2);
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                    Log.e("PrinterUtil", "Error printing QR code: " + e.getMessage());
//                                }
//                            } else {
//                                Log.w("PrinterUtil", "QR code bitmap is null");
//                            }
//                        } else {
//                            Log.w("PrinterUtil", "No QR code URL available - skipping QR code");
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        Log.e("PrinterUtil", "Exception in QR code section: " + e.getMessage());
//                    }
//                } // END QR code condition

//                mUsbThermalPrinter.setAlgin(0);
//                mUsbThermalPrinter.setTextSize(22);
//                mUsbThermalPrinter.addString("----------------------------------");
//                mUsbThermalPrinter.printString();
//                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ CHANGED: Receipt Number (after QR) with padLeft â”€â”€â”€â”€â”€â”€


                String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                String receiptNum = (details.getData().getInvoice_id() != null)
                        ? details.getData().getInvoice_id() : "";
                mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
                mUsbThermalPrinter.printString();


                String rawDateTimee = details.getData().getPurchase_date_time();

                String vsdcDate2 = "";
                String vsdcTime2 = "";
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                    Date parsedDate = inputFormat.parse(rawDateTimee);
                    if (parsedDate != null) {
                        vsdcDate2 = formatReceiptDate(parsedDate);
                        vsdcTime2 = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedDate);
                    }
                } catch (ParseException e) {
                    try {
                        SimpleDateFormat fallbackFormat = new SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault());
                        Date parsedDate = fallbackFormat.parse(rawDateTimee);
                        if (parsedDate != null) {
                            vsdcDate2 = formatReceiptDate(parsedDate);
                            vsdcTime2 = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedDate);
                        }
                    } catch (ParseException e2) {
                        Log.e("DateFormat", "Parsing failed: " + e2.getMessage());
                        vsdcDate2 = rawDateTimee; // fallback
                    }
                }

                String dateLabel2 = "DATE: " + vsdcDate2;
                String timeLabel2 = "TIME: " + vsdcTime2;
                int spaces2 = TSIZE22 - dateLabel2.length() - timeLabel2.length();
                if (spaces2 < 1) spaces2 = 1;
                StringBuilder dateLine2 = new StringBuilder(dateLabel2);
                for (int i = 0; i < spaces2; i++) dateLine2.append(" ");
                dateLine2.append(timeLabel2);

                mUsbThermalPrinter.addString(dateLine2.toString());
                mUsbThermalPrinter.printString();

                // â”€â”€ CHANGED: MRC No with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//                String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
//                String mrcValue = (vsdc != null && vsdc.getMrcNo() != null)
//                        ? vsdc.getMrcNo() + "." : ".";
//                mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
//                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setAlgin(0);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();

// âœ… IMPORTANT: Reset printer state after QR code attempt
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setGray(6);

// Now print THANK YOU
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_appreciate_biz));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setLineSpace(3);
                mUsbThermalPrinter.setTextSize(20);

                mUsbThermalPrinter.addString("*** END ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.addString(" ");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.reset();

            } catch (Exception e) {
                Log.e("PrinterUtil_Debug", "printSaleType(rra): Exception caught during printing", e);
                e.printStackTrace();
                System.out.println(e.toString());
                Log.e("PrinterUtil", "CRASH in printSaleType: " + e.getMessage(), e);

                Result = e.toString();
                if (Result.contains("NoPaperException")) {
                    Log.e("PrinterUtil_Debug", "printSaleType(rra): NoPaperException detected!");
                    nopaper = true;
                } else if (Result.contains("OverHeatException")) {
                    Log.e("PrinterUtil_Debug", "printSaleType(rra): OverHeatException detected!");
                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                } else {
                    Log.e("PrinterUtil_Debug", "printSaleType(rra): General PRINTERR detected!");
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }
            } finally {
                Log.e("PrinterUtil_Debug", "printSaleType(rra): finally block reached");
                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                if (nopaper) {
                    Log.e("PrinterUtil_Debug", "printSaleType(rra): nopaper flag is true, notifying handler");
                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                    nopaper = false;
                    return;
                }
            }
            Log.e("PrinterUtil_Debug", "printSaleType(): layout 'type3' matches, but it has no printing logic implemented!");
        } else if (receiptType.equalsIgnoreCase("zra")) {
                Log.e("PrinterUtil_Debug", "printSaleType(): routing to 'rra' layout");
                try {
                    String rcptType = "";
                    if (details.getData() != null && details.getData().getRcptType() != null) {
                        rcptType = details.getData().getRcptType();
                    }else {
                        rcptType = "Proforma";
                    }
                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setLeftIndent(1);
                    mUsbThermalPrinter.setLineSpace(3);
                    mUsbThermalPrinter.setTextSize(20);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setGray(6);

                    mUsbThermalPrinter.addString("CIS Version : 1.0.1");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(3);

                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                    // Load logo bitmap
                    Bitmap logoBitmap = BitmapFactory.decodeResource(
                            context.getResources(),
                            R.drawable.image22
                    );

// Optional: resize logo for printer
                    logoBitmap = Bitmap.createScaledBitmap(
                            logoBitmap,
                            400,   // width (safe for 58mm)
                            200,    // height
                            true
                    );

                    mUsbThermalPrinter.printLogo(logoBitmap, false);
                    mUsbThermalPrinter.walkPaper(1);


                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(32);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(true);

                    Log.d("PrinterUtil", "Checking data before printing store info...");
                    if (details == null) {
                        Log.e("PrinterUtil", "DEBUG: details object is completely NULL!");
                    } else if (details.getData() == null) {
                        Log.e("PrinterUtil", "DEBUG: details.getData() is NULL!");
                    } else if (details.getData().getStore() == null) {
                        Log.e("PrinterUtil", "DEBUG: details.getData().getStore() is NULL!");
                    } else {
                        Log.d("PrinterUtil", "Store Name: " + details.getData().getStore().getStore_name());
                    }

                    mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(26);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(2);


                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(24);
                    mUsbThermalPrinter.setGray(6);

                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) +details.getData().getTpin_no());
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    if (rcptType.equalsIgnoreCase("P" ) || rcptType.equalsIgnoreCase("Proforma")) {
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.addString(details.getData().getRcptType() );
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    } else if (rcptType.equalsIgnoreCase("T")|| rcptType.equalsIgnoreCase("Training")) {
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.addString(details.getData().getRcptType() + " MODE");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.addString(details.getData().getRcptType());
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    }
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setGray(6);
                    if (rcptType == null){
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    }
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_welcome));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

                    String buyerName = details.getData() != null && details.getData().getCustomer_name() != null
                            ? details.getData().getCustomer_name()
                            : "";

                    // â”€â”€ CHANGED: Buyer info with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
                    mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setGray(6);

                    String buyerTin = "";
                    if (details.getData() != null && details.getData().getBuyers_tpin() != null) {
                        buyerTin = details.getData().getBuyers_tpin();
                    }
                    String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
                    mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
                    mUsbThermalPrinter.printString();

                    String buyerContact = "";
                    if (details.getData() != null && details.getData().getCustomer_mob_no() != null) {
                        buyerContact = details.getData().getCustomer_mob_no();
                    }
                    String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
                    mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.walkPaper(1);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                    mUsbThermalPrinter.setTextSize(22);

                    mUsbThermalPrinter.setGray(6);

                    mUsbThermalPrinter.setGray(6);

                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setGray(6);

                    for (SalesItem item : details.getData().getSalesItem()) {
                        productname = item.getProduct_name();
                        numberOfItems++;
                        if (productname.toLowerCase().startsWith("bulk oil")) {
                            looseOil = true;
                        } else {
                            looseOil = false;
                        }

                        // Line 1: Product Name (bold)
                        mUsbThermalPrinter.setTextSize(24);
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.addString(item.getProduct_name());
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);

                        // --- Build 3-column line: [rate x]   [qty]   [amount taxCode] ---
                        String taxCode = "";
                        if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                            taxCode = item.getTax_details().getCode();
                        }

                        // Left: "33600.00x"
                        String rateCol = FunUtils.INSTANCE.formatPrintPrice(item.getTax_inclusive_price()) + "x";

                        // Middle: "0.200 (KG)"  or just quantity + UOM
                        String qtyCol = FunUtils.INSTANCE.DtoString(Double.parseDouble(item.getQuantity()));

                        // Right: "6720.00B"
                        String amountCol = FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_amount())) + taxCode;

                        // Total line width for textSize 22 (34 chars for 58mm printer)
                        int totalWidth = TSIZE22; // e.g. 34

                        // Right column occupies fixed 10 chars from right
                        int rightColWidth = 10;
                        // Middle column occupies fixed 6 chars
                        int midColWidth = 8;
                        // Left col gets remaining
                        int leftColWidth = totalWidth - midColWidth - rightColWidth;

                        // Format: left-pad qty to midColWidth, right-pad amount to rightColWidth
                        String line2 = String.format(
                                "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                                rateCol,
                                qtyCol,
                                amountCol
                        );

                        mUsbThermalPrinter.addString(line2);
                        mUsbThermalPrinter.printString();

                        // Discount line (if applicable)
                        if (item.getDiscount_rate() < 0) {
                            String discountText = "discount " + item.getDiscount_rate() + "%";

                            double discountedTotal = item.getTotal_amount()
                                    + (item.getTotal_amount() * item.getDiscount_rate() / 100);

                            String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(discountedTotal));

                            // Left: "discount -25%"   Right: "5040.00" right-aligned
                            String discountLine = String.format(
                                    "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                    discountText,
                                    discountAmountStr
                            );

                            mUsbThermalPrinter.addString(discountLine);
                            mUsbThermalPrinter.printString();
                        }

                        mUsbThermalPrinter.walkPaper(1);
                    }



                    mUsbThermalPrinter.setTextSize(22);
                    String spotAmount = details.getData().getSpot_discount_amount();
                    if (hasSpotDiscount(spotAmount)) {
                        String pct = details.getData().getSpot_discount_percentage();
                        String pctVal = (pct != null && !pct.isEmpty()) ? pct : "0";
                        String sptdiscount = receiptStr(R.string.print_on_spot_discount_label, pctVal);
                        mUsbThermalPrinter.addString(sptdiscount + padLeft(FunUtils.INSTANCE.formatPrintPrice(spotAmount), TSIZE22 - sptdiscount.length()));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    } else {
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    }
                    // âœ… Check by receipt type code
                    if (rcptType.equalsIgnoreCase("P") ||
                            rcptType.equalsIgnoreCase("T") ||
                            rcptType.equalsIgnoreCase("C") ||
                            rcptType.equalsIgnoreCase("Proforma") ||
                            rcptType.equalsIgnoreCase("Training") ||
                            rcptType.equalsIgnoreCase("Copy")) {

                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    }

                    mUsbThermalPrinter.setTextSize(26);
                    mUsbThermalPrinter.setBold(true);
                    String label = "TOTAL(" + currency + "):";
                    String value = FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total());
                    // Right align the value using padLeft correctly
                    int padding = TSIZE26 - label.length();
                    mUsbThermalPrinter.addString(label + padLeft(value, padding));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.walkPaper(2);
                    mUsbThermalPrinter.setTextSize(22);

                    // Get tax summary list
                    List<TaxSummary> taxSummaryList = details.getData().getTax_summery();

                    if (taxSummaryList != null && !taxSummaryList.isEmpty()) {

                        for (TaxSummary taxSummary : taxSummaryList) {
                            String code = taxSummary.getCode();
                            Double taxableValue = taxSummary.getTaxable_value();

                            if (code != null ) {
                                String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                                mUsbThermalPrinter.addString(taxLabel + padLeft(
                                        FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                        TSIZE22 - taxLabel.length()
                                ));
                                mUsbThermalPrinter.printString();
                            }
                        }

                        for (TaxSummary taxSummary : taxSummaryList) {
                            String code = taxSummary.getCode();
                            Double taxAmount = taxSummary.getTax_amount();

                            if (taxAmount != null && taxAmount > 0) {
                                String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                                mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                        FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmount)),
                                        TSIZE22 - taxAmountLabel.length()
                                ));
                                mUsbThermalPrinter.printString();
                            }
                        }

                        String totalTaxAmount = details.getData().getTax_amount();
                        if (totalTaxAmount != null && !totalTaxAmount.equals("0")) {
                            String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                            mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(totalTaxAmount),
                                    TSIZE22 - totalTaxLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }
                    mUsbThermalPrinter.walkPaper(1);



                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(20);
                    // â”€â”€ Payment / Items count (skip for Proforma) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    if (!rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        String paymentType = details.getData().getPayment_type().trim();
                        String grandTotal = FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total());

                        String paymentLabel = "";
                        if (paymentType.equals("01"))      paymentLabel = receiptStr(R.string.payment_type_cash);
                        else if (paymentType.equals("02")) paymentLabel = receiptStr(R.string.payment_type_credit);
                        else if (paymentType.equals("03")) paymentLabel = receiptStr(R.string.payment_type_cash_credit);
                        else if (paymentType.equals("04")) paymentLabel = receiptStr(R.string.payment_type_bank_check);
                        else if (paymentType.equals("05")) paymentLabel = receiptStr(R.string.payment_type_card);
                        else if (paymentType.equals("06")) paymentLabel = receiptStr(R.string.payment_type_m_money);
                        else if (paymentType.equals("07")) paymentLabel = receiptStr(R.string.payment_type_other);
                        else if (paymentType.equalsIgnoreCase("CASH")) paymentLabel = receiptStr(R.string.payment_type_cash);
                        else if (paymentType.equalsIgnoreCase("CARD")) paymentLabel = receiptStr(R.string.payment_type_card);
                        else if (paymentType.equalsIgnoreCase("M-MONEY")) paymentLabel = receiptStr(R.string.payment_type_m_money);

                        if (!paymentLabel.isEmpty()) {
                            mUsbThermalPrinter.addString(paymentLabel + padLeft(grandTotal, TSIZE20 - paymentLabel.length()));
                            mUsbThermalPrinter.printString();
                        }

                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(Integer.toString(details.getData().getSalesItem().size()), TSIZE22 - 6));
                        mUsbThermalPrinter.printString();
                    } else {
                        mUsbThermalPrinter.walkPaper(1); // maintain spacing for Proforma
                    }


                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                    if (rcptType.equalsIgnoreCase("P" ) || rcptType.equalsIgnoreCase("Proforma")) {
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.addString(details.getData().getRcptType() );
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    } else if (rcptType.equalsIgnoreCase("T")|| rcptType.equalsIgnoreCase("Training")) {
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.addString(details.getData().getRcptType() + " Mode");
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    } else if (rcptType.equalsIgnoreCase("C") || rcptType.equalsIgnoreCase("Copy")) {
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.setTextSize(22);
                        mUsbThermalPrinter.setAlgin(1);
                        mUsbThermalPrinter.addString(details.getData().getRcptType());
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.addString("----------------------------------");
                        mUsbThermalPrinter.printString();
                    }
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setGray(6);
                    VsdcReceipt vsdc = (details.getData().getVsdc_reciept() != null && !details.getData().getVsdc_reciept().isEmpty())
                            ? details.getData().getVsdc_reciept().get(0) : null;

                    String rawDateTime = (vsdc != null) ? vsdc.getVsdcRcptPbctDate() : "";

                    String vsdcDate = "";
                    String vsdcTime = "";
                    if (rawDateTime != null && !rawDateTime.isEmpty()) {
                        try {
                            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                            Date parsedDate = inputFormat.parse(rawDateTime);
                            if (parsedDate != null) {
                                vsdcDate = formatReceiptDate(parsedDate);
                                vsdcTime = new SimpleDateFormat("HH:mm:ss",    Locale.getDefault()).format(parsedDate);
                            }
                        } catch (ParseException e) {
                            Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                            vsdcDate = rawDateTime; // fallback
                        }
                    }

                    String dateLabel = receiptStr(R.string.receipt_label_date) + " " + vsdcDate;
                    String timeLabel = receiptStr(R.string.receipt_label_time) + " " + vsdcTime;
                    int spaces = TSIZE22 - dateLabel.length() - timeLabel.length();
                    if (spaces < 1) spaces = 1;
                    StringBuilder dateLine = new StringBuilder(dateLabel);
                    for (int i = 0; i < spaces; i++) dateLine.append(" ");
                    dateLine.append(timeLabel);

                    mUsbThermalPrinter.addString(dateLine.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED: SDC ID with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                    String sdcIdValue = (vsdc != null && vsdc.getSdcId() != null)
                            ? vsdc.getSdcId() : "";
                    mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcIdValue, TSIZE22 - sdcIdLabel.length()));
                    mUsbThermalPrinter.printString();

                    String rcptTypeValue = details.getData().getRcptType() != null ? details.getData().getRcptType() : "N";
                    String receipttype = (vsdc != null ? vsdc.getSales_type() : "") + "" + "S";

                    // â”€â”€ CHANGED: Receipt Number (SDC section) with padLeft â”€â”€â”€
                    String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
                    String sdcRcptValue = (vsdc != null ? vsdc.getTotRcptNo() : "")
                            + "/" + (vsdc != null ? vsdc.getTotRcptNo() : "")
                            + " " + receipttype;
                    mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(1);
                    if (!rcptType.equalsIgnoreCase("T") && !rcptType.equalsIgnoreCase("Training") &&
                            !rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + (vsdc != null ? vsdc.getIntrlData() : ""));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + (vsdc != null ? vsdc.getRcptSign() : ""));
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.walkPaper(1);
                    }

// GENERATE AND PRINT QR CODE HERE â¬‡ï¸
                    if (!rcptType.equalsIgnoreCase("T") && !rcptType.equalsIgnoreCase("Training") &&
                            !rcptType.equalsIgnoreCase("P") && !rcptType.equalsIgnoreCase("Proforma")) {
                        try {
                            if (vsdc != null &&
                                    vsdc.getQrCodeUrl() != null &&
                                    !vsdc.getQrCodeUrl().isEmpty()) {

                                mUsbThermalPrinter.reset();
                                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                                String qrData = vsdc.getQrCodeUrl();
                                Bitmap qrBitmap = generateQRCodeBitmap(qrData, 250, 250);

                                if (qrBitmap != null) {
                                    try {
                                        mUsbThermalPrinter.setGray(6);
                                        mUsbThermalPrinter.printLogo(qrBitmap, false);
                                        mUsbThermalPrinter.walkPaper(2);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Log.e("PrinterUtil", "Error printing QR code: " + e.getMessage());
                                    }
                                } else {
                                    Log.w("PrinterUtil", "QR code bitmap is null");
                                }
                            } else {
                                Log.w("PrinterUtil", "No QR code URL available - skipping QR code");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("PrinterUtil", "Exception in QR code section: " + e.getMessage());
                        }
                    } // END QR code condition

                    mUsbThermalPrinter.setAlgin(0);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ CHANGED: Receipt Number (after QR) with padLeft â”€â”€â”€â”€â”€â”€


                    String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                    String receiptNum = (details.getData().getInvoice_id() != null)
                            ? details.getData().getInvoice_id() : "";
                    mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
                    mUsbThermalPrinter.printString();


                    String rawDateTimee = details.getData().getPurchase_date_time();

                    String vsdcDate2 = "";
                    String vsdcTime2 = "";
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                        Date parsedDate = inputFormat.parse(rawDateTimee);
                        if (parsedDate != null) {
                            vsdcDate2 = formatReceiptDate(parsedDate);
                            vsdcTime2 = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedDate);
                        }
                    } catch (ParseException e) {
                        try {
                            SimpleDateFormat fallbackFormat = new SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault());
                            Date parsedDate = fallbackFormat.parse(rawDateTimee);
                            if (parsedDate != null) {
                                vsdcDate2 = formatReceiptDate(parsedDate);
                                vsdcTime2 = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedDate);
                            }
                        } catch (ParseException e2) {
                            Log.e("DateFormat", "Parsing failed: " + e2.getMessage());
                            vsdcDate2 = rawDateTimee; // fallback
                        }
                    }

                    String dateLabel2 = "DATE: " + vsdcDate2;
                    String timeLabel2 = "TIME: " + vsdcTime2;
                    int spaces2 = TSIZE22 - dateLabel2.length() - timeLabel2.length();
                    if (spaces2 < 1) spaces2 = 1;
                    StringBuilder dateLine2 = new StringBuilder(dateLabel2);
                    for (int i = 0; i < spaces2; i++) dateLine2.append(" ");
                    dateLine2.append(timeLabel2);

                    mUsbThermalPrinter.addString(dateLine2.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED: MRC No with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
                    String mrcValue = (vsdc != null && vsdc.getMrcNo() != null)
                            ? vsdc.getMrcNo() + "." : ".";
                    mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(0);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();

// âœ… IMPORTANT: Reset printer state after QR code attempt
                    mUsbThermalPrinter.reset();
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setTextSize(22);
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setGray(6);

// Now print THANK YOU
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_appreciate_biz));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                    mUsbThermalPrinter.setLineSpace(3);
                    mUsbThermalPrinter.setTextSize(20);

                    mUsbThermalPrinter.addString("*** END ***");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(5);
                    mUsbThermalPrinter.addString(" ");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(5);
                    mUsbThermalPrinter.reset();

                } catch (Exception e) {
                    Log.e("PrinterUtil_Debug", "printSaleType(rra): Exception caught during printing", e);
                    e.printStackTrace();
                    System.out.println(e.toString());
                    Log.e("PrinterUtil", "CRASH in printSaleType: " + e.getMessage(), e);

                    Result = e.toString();
                    if (Result.contains("NoPaperException")) {
                        Log.e("PrinterUtil_Debug", "printSaleType(rra): NoPaperException detected!");
                        nopaper = true;
                    } else if (Result.contains("OverHeatException")) {
                        Log.e("PrinterUtil_Debug", "printSaleType(rra): OverHeatException detected!");
                        handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                    } else {
                        Log.e("PrinterUtil_Debug", "printSaleType(rra): General PRINTERR detected!");
                        handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                    }
                } finally {
                    Log.e("PrinterUtil_Debug", "printSaleType(rra): finally block reached");
                    handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                    if (nopaper) {
                        Log.e("PrinterUtil_Debug", "printSaleType(rra): nopaper flag is true, notifying handler");
                        handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                        nopaper = false;
                        return;
                    }
                }
                Log.e("PrinterUtil_Debug", "printSaleType(): layout 'zra' matches, but it has no printing logic implemented!");


        } else if (receiptType.equalsIgnoreCase("type5")) {
            Log.e("PrinterUtil_Debug", "printSaleType(): layout 'type5' matches, but it has no printing logic implemented!");
        } else if (receiptType.equalsIgnoreCase("type6")) {
            Log.e("PrinterUtil_Debug", "printSaleType(): layout 'type6' matches, but it has no printing logic implemented!");
        } else {
            Log.e("PrinterUtil_Debug", "printSaleType(): receiptType '" + receiptType + "' did not match any known layout ('rra', 'moz', 'type3'-'type6')!");
        }
    }


    public void printCopyReceipt(CopyReceiptRes details) {
        com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper orgHelper = new com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper(context);
        String receiptType = orgHelper.getOrganisationData().getReciept_type();
        if (receiptType == null || receiptType.trim().isEmpty()) {
            receiptType = "rra";
        }

        if (receiptType.equalsIgnoreCase("default")) {
            try {
                if (details == null || details.getData() == null) {
                Log.e("PrinterUtil", "Receipt data is null");
                handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                return;
            }

            CopyReceiptRes.Data data = details.getData();

            // â”€â”€ HEADER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setLeftIndent(1);
            mUsbThermalPrinter.setLineSpace(3);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("CIS Version : 1.0.1");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(3);

            // Logo
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.image22);
            logoBitmap = Bitmap.createScaledBitmap(logoBitmap, 400, 200, true);
            mUsbThermalPrinter.printLogo(logoBitmap, false);
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ STORE INFO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(32);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(true);
            String storeName = (data.getStore() != null && data.getStore().getStore_name() != null)
                    ? data.getStore().getStore_name().toUpperCase() : "";
            mUsbThermalPrinter.addString(storeName);
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(26);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            String storeAddress = (data.getStore() != null && data.getStore().getAddress() != null)
                    ? data.getStore().getAddress().toUpperCase() : "";
            mUsbThermalPrinter.addString(storeAddress);
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(24);
            mUsbThermalPrinter.setGray(6);
            String tpinNo = (data.getTpin_no() != null) ? data.getTpin_no() : "";
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) + " " + tpinNo);
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ RECEIPT TYPE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            String rcptTypeRaw = receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault());
            mUsbThermalPrinter.setTextSize(24);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.addString(rcptTypeRaw);
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ REFUND LABEL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.setTextSize(23);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund).toUpperCase(Locale.getDefault()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

            // â”€â”€ CHANGED 1: Ref. Normal Receipt with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            String returnedInvoiceId = (data.getReturned_invoice_id() != null)
                    ? data.getReturned_invoice_id() : "";
            String refLabel = receiptStr(R.string.receipt_label_ref_normal);
            mUsbThermalPrinter.addString(refLabel + padLeft(returnedInvoiceId, TSIZE22 - refLabel.length()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund_approved_only));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ CHANGED 2: Date parsed properly with yyyyMMddHHmmss â”€â”€
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(6);

            CopyReceiptRes.VsdcReceipt vsdcRef = (data.getVsdc_reciept() != null && !data.getVsdc_reciept().isEmpty())
                    ? data.getVsdc_reciept().get(0) : null;

            String rawVsdcDate = (vsdcRef != null && vsdcRef.getVsdcRcptPbctDate() != null)
                    ? vsdcRef.getVsdcRcptPbctDate() : "";
            String formattedDate = "";
            String formattedTime = "";
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                Date parsedVsdcDate = inputFormat.parse(rawVsdcDate);
                if (parsedVsdcDate != null) {
                    formattedDate = formatReceiptDate(parsedVsdcDate);
                    formattedTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedVsdcDate);
                }
            } catch (ParseException e) {
                Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                formattedDate = rawVsdcDate; // fallback
            }

            String vsdcDateLabel = receiptStr(R.string.receipt_label_date) + " " + formattedDate;
            String vsdcTimeLabel = receiptStr(R.string.receipt_label_time) + " " + formattedTime;
            int vsdcSpaces = TSIZE22 - vsdcDateLabel.length() - vsdcTimeLabel.length();
            if (vsdcSpaces < 1) vsdcSpaces = 1;
            StringBuilder vsdcDateLine = new StringBuilder(vsdcDateLabel);
            for (int i = 0; i < vsdcSpaces; i++) vsdcDateLine.append(" ");
            vsdcDateLine.append(vsdcTimeLabel);

//            mUsbThermalPrinter.addString(vsdcDateLine.toString());
//            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ CHANGED 1: Buyer info with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            String buyerName = (data.getCustomer_name() != null) ? data.getCustomer_name() : "";
            String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
            mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
            mUsbThermalPrinter.printString();

            String buyerTin = (data.getBuyers_tpin() != null) ? data.getBuyers_tpin() : "";
            String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
            mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
            mUsbThermalPrinter.printString();

            String buyerContact = (data.getCustomer_mob_no() != null) ? data.getCustomer_mob_no() : "";
            String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
            mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ ITEMS LOOP â€” uses returned_items â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (data.getReturned_items() != null) {
                for (CopyReceiptRes.ReturnedItem item : data.getReturned_items()) {
                    if (item == null) continue;

                    productname = (item.getProduct_name() != null) ? item.getProduct_name() : "";
                    numberOfItems++;

                    // Line 1: Product Name (bold)
                    mUsbThermalPrinter.setTextSize(24);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.addString(productname);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);

                    String taxCode = "";
                    if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                        taxCode = item.getTax_details().getCode();
                    }

                    String rateCol = (item.getRetail_price() != null
                            ? FunUtils.INSTANCE.formatPrintPrice(String.valueOf(item.getRetail_price()))
                            : "0.00") + "x";

                    String qtyCol = FunUtils.INSTANCE.DtoString(
                            item.getReturn_quantity() != null
                                    ? item.getReturn_quantity().doubleValue() : 0.0);

                    String amountCol = "-" + (item.getTotal_amount() != null
                            ? FunUtils.INSTANCE.formatPrintPrice(String.valueOf(item.getTotal_amount()))
                            : "0.00") + taxCode;

                    int totalWidth    = TSIZE22;
                    int rightColWidth = 10;
                    int midColWidth   = 8;
                    int leftColWidth  = totalWidth - midColWidth - rightColWidth;

                    String line2 = String.format(
                            "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                            rateCol,
                            qtyCol,
                            amountCol
                    );

                    mUsbThermalPrinter.addString(line2);
                    mUsbThermalPrinter.printString();

                    Double discountRate    = item.getDiscount_rate();
                    Double itemTotal       = item.getTotal_amount();
                    Double discountedTotal = item.getDiscounted_total();

                    if (discountRate != null && discountRate < 0
                            && itemTotal != null && itemTotal > 0
                            && discountedTotal != null) {

                        String discountText = "Discount " + discountRate.intValue() + "%";

                        String finalAmountStr = FunUtils.INSTANCE.formatPrintPrice(
                                Double.toString(discountedTotal));

                        String discountLine = String.format(
                                "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                discountText,
                                finalAmountStr
                        );

                        mUsbThermalPrinter.addString(discountLine);
                        mUsbThermalPrinter.printString();
                    }

                    mUsbThermalPrinter.walkPaper(1);
                }
            }

            // â”€â”€ TOTALS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.setTextSize(23);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            String grandTotal = (data.getGrand_total() != null)
                    ? FunUtils.INSTANCE.formatPrintPrice(data.getGrand_total()) : "0.00";

            mUsbThermalPrinter.setTextSize(26);
            mUsbThermalPrinter.setBold(true);
            String totalLabel = receiptStr(R.string.receipt_label_total) + "(" + currency + "):";
            mUsbThermalPrinter.addString(totalLabel + padLeft(grandTotal, TSIZE26 - totalLabel.length()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);

            // â”€â”€ TAX SUMMARY LOOP â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            List<CopyReceiptRes.TaxSummery> taxSummaryList = data.getTax_summery();
            if (taxSummaryList != null && !taxSummaryList.isEmpty()) {

                for (CopyReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                    if (taxSummary.getCode() != null) {
                        String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                        double taxableValue = (taxSummary.getTaxable_value() != null)
                                ? taxSummary.getTaxable_value() : 0.0;
                        mUsbThermalPrinter.addString(taxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                TSIZE22 - taxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                for (CopyReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                    Double taxAmt = taxSummary.getTax_amount();
                    String code = taxSummary.getCode();
                    if (taxAmt != null && taxAmt != 0.0 && code != null) {
                        String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                        mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmt)),
                                TSIZE22 - taxAmountLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                String totalTaxAmount = data.getTax_amount();
                if (totalTaxAmount != null && !totalTaxAmount.equals("0")
                        && !totalTaxAmount.equals("0.00")) {
                    String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                    mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                            FunUtils.INSTANCE.formatPrintPrice(totalTaxAmount),
                            TSIZE22 - totalTaxLabel.length()
                    ));
                    mUsbThermalPrinter.printString();
                }
            }

            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            String grandTotall = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(details.getData().getGrand_total()));
            String paymentLabel = receiptStr(R.string.payment_type_cash).replace(":", "");

            if (!paymentLabel.isEmpty()) {
                mUsbThermalPrinter.addString(paymentLabel + padLeft(grandTotall, TSIZE22 - paymentLabel.length()));
                mUsbThermalPrinter.printString();
            }

            // Items count
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            int itemsCount = (data.getReturned_items() != null)
                    ? data.getReturned_items().size() : 0;
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(Integer.toString(itemsCount), TSIZE22 - 6));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ SDC INFORMATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_sdc_info));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

            if (data.getVsdc_reciept() != null && !data.getVsdc_reciept().isEmpty()) {
                CopyReceiptRes.VsdcReceipt vsdc = data.getVsdc_reciept().get(0);

                // â”€â”€ CHANGED 2: Reuse properly parsed formattedDate/formattedTime â”€â”€
                mUsbThermalPrinter.addString(vsdcDateLine.toString());
                mUsbThermalPrinter.printString();

                // â”€â”€ CHANGED 1: SDC ID with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String sdcId = (vsdc.getSdcId() != null) ? vsdc.getSdcId() : "";
                String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                mUsbThermalPrinter.printString();

                // â”€â”€ CHANGED 1: Receipt Number (SDC) with padLeft â”€â”€â”€â”€â”€
                String rcptNo = (vsdc.getRcptNo() != null && vsdc.getRcptNo() != 0)
                        ? String.valueOf(vsdc.getRcptNo()) : "";
                String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
                String sdcRcptValue = rcptNo + "/" + vsdc.getTotRcptNo() + " CR";
                mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                String intrlData = (vsdc.getIntrlData() != null) ? vsdc.getIntrlData() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + intrlData);
                mUsbThermalPrinter.printString();

                String rcptSign = (vsdc.getRcptSign() != null) ? vsdc.getRcptSign() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + rcptSign);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ CHANGED 3: Receipt Number, Date/Time, MRC No after receipt signature â”€â”€
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // Receipt Number
                String invoiceId = (data.getReturned_invoice_id() != null)
                        ? data.getReturned_invoice_id() : "";
                String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                mUsbThermalPrinter.addString(rcptNumLabel + padLeft(invoiceId, TSIZE22 - rcptNumLabel.length()));
                mUsbThermalPrinter.printString();

                // â”€â”€ CHANGED 2: Purchase date properly parsed â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String rawPurchaseDateTime = (data.getReturned_date() != null)
                        ? data.getReturned_date() : "";
                String purchaseDate = "";
                String purchaseTime = "";
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                    Date parsedPurchaseDate = inputFormat.parse(rawPurchaseDateTime);
                    if (parsedPurchaseDate != null) {
                        purchaseDate = formatReceiptDate(parsedPurchaseDate);
                        purchaseTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedPurchaseDate);
                    }
                } catch (ParseException e) {
                    Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                    purchaseDate = rawPurchaseDateTime; // fallback
                }

                String purchaseDateLabel = receiptStr(R.string.receipt_label_date) + " " + purchaseDate;
                String purchaseTimeLabel = receiptStr(R.string.receipt_label_time) + " " + purchaseTime;
                int purchaseSpaces = TSIZE22 - purchaseDateLabel.length() - purchaseTimeLabel.length();
                if (purchaseSpaces < 1) purchaseSpaces = 1;
                StringBuilder purchaseDateLine = new StringBuilder(purchaseDateLabel);
                for (int i = 0; i < purchaseSpaces; i++) purchaseDateLine.append(" ");
                purchaseDateLine.append(purchaseTimeLabel);

                mUsbThermalPrinter.addString(purchaseDateLine.toString());
                mUsbThermalPrinter.printString();

                // â”€â”€ CHANGED 1: MRC No with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
                String mrcValue = (vsdc.getMrcNo() != null) ? vsdc.getMrcNo() + "." : ".";
                mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
                mUsbThermalPrinter.printString();
            }

            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ FOOTER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_appreciate_biz));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(3);

            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.addString("*** END ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(5);
            mUsbThermalPrinter.addString(" ");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(5);
            mUsbThermalPrinter.reset();

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("PrinterUtil", "Print error: " + e.getMessage(), e);
            Result = e.toString();
            if (Result.contains("NoPaperException")) {
                nopaper = true;
            } else if (Result.contains("OverHeatException")) {
                handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
            } else {
                handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
            }
        } finally {
            handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
            if (nopaper) {
                handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                nopaper = false;
                return;
            }
        }
        } else if (receiptType.equalsIgnoreCase("rra")) {
            try {
                if (details == null || details.getData() == null) {
                    Log.e("PrinterUtil", "Receipt data is null");
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                    return;
                }

                CopyReceiptRes.Data data = details.getData();

                // â”€â”€ HEADER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setLeftIndent(1);
                mUsbThermalPrinter.setLineSpace(3);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("CIS Version : 1.0.1");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                // Logo
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.image22);
                logoBitmap = Bitmap.createScaledBitmap(logoBitmap, 400, 200, true);
                mUsbThermalPrinter.printLogo(logoBitmap, false);
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ STORE INFO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(32);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);
                String storeName = (data.getStore() != null && data.getStore().getStore_name() != null)
                        ? data.getStore().getStore_name().toUpperCase() : "";
                mUsbThermalPrinter.addString(storeName);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                String storeAddress = (data.getStore() != null && data.getStore().getAddress() != null)
                        ? data.getStore().getAddress().toUpperCase() : "";
                mUsbThermalPrinter.addString(storeAddress);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setGray(6);
                String tpinNo = (data.getTpin_no() != null) ? data.getTpin_no() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) + " " + tpinNo);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ RECEIPT TYPE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String rcptTypeRaw = receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault());
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(rcptTypeRaw);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ REFUND LABEL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

                // â”€â”€ CHANGED 1: Ref. Normal Receipt with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String returnedInvoiceId = (data.getReturned_invoice_id() != null)
                        ? data.getReturned_invoice_id() : "";
                String refLabel = receiptStr(R.string.receipt_label_ref_normal);
                mUsbThermalPrinter.addString(refLabel + padLeft(returnedInvoiceId, TSIZE22 - refLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_refund_approved_only));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ CHANGED 2: Date parsed properly with yyyyMMddHHmmss â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setGray(6);

                CopyReceiptRes.VsdcReceipt vsdcRef = (data.getVsdc_reciept() != null && !data.getVsdc_reciept().isEmpty())
                        ? data.getVsdc_reciept().get(0) : null;

                String rawVsdcDate = (vsdcRef != null && vsdcRef.getVsdcRcptPbctDate() != null)
                        ? vsdcRef.getVsdcRcptPbctDate() : "";
                String formattedDate = "";
                String formattedTime = "";
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                    Date parsedVsdcDate = inputFormat.parse(rawVsdcDate);
                    if (parsedVsdcDate != null) {
                        formattedDate = formatReceiptDate(parsedVsdcDate);
                        formattedTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedVsdcDate);
                    }
                } catch (ParseException e) {
                    Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                    formattedDate = rawVsdcDate; // fallback
                }

                String vsdcDateLabel = receiptStr(R.string.receipt_label_date) + " " + formattedDate;
                String vsdcTimeLabel = receiptStr(R.string.receipt_label_time) + " " + formattedTime;
                int vsdcSpaces = TSIZE22 - vsdcDateLabel.length() - vsdcTimeLabel.length();
                if (vsdcSpaces < 1) vsdcSpaces = 1;
                StringBuilder vsdcDateLine = new StringBuilder(vsdcDateLabel);
                for (int i = 0; i < vsdcSpaces; i++) vsdcDateLine.append(" ");
                vsdcDateLine.append(vsdcTimeLabel);

//            mUsbThermalPrinter.addString(vsdcDateLine.toString());
//            mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ CHANGED 1: Buyer info with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String buyerName = (data.getCustomer_name() != null) ? data.getCustomer_name() : "";
                String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
                mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerTin = (data.getBuyers_tpin() != null) ? data.getBuyers_tpin() : "";
                String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
                mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerContact = (data.getCustomer_mob_no() != null) ? data.getCustomer_mob_no() : "";
                String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
                mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ ITEMS LOOP â€” uses returned_items â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                if (data.getReturned_items() != null) {
                    for (CopyReceiptRes.ReturnedItem item : data.getReturned_items()) {
                        if (item == null) continue;

                        productname = (item.getProduct_name() != null) ? item.getProduct_name() : "";
                        numberOfItems++;

                        // Line 1: Product Name (bold)
                        mUsbThermalPrinter.setTextSize(24);
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.addString(productname);
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);

                        String taxCode = "";
                        if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                            taxCode = item.getTax_details().getCode();
                        }

                        String rateCol = (item.getRetail_price() != null
                                ? FunUtils.INSTANCE.formatPrintPrice(String.valueOf(item.getRetail_price()))
                                : "0.00") + "x";

                        String qtyCol = FunUtils.INSTANCE.DtoString(
                                item.getReturn_quantity() != null
                                        ? item.getReturn_quantity().doubleValue() : 0.0);

                        String amountCol = "-" + (item.getTotal_amount() != null
                                ? FunUtils.INSTANCE.formatPrintPrice(String.valueOf(item.getTotal_amount()))
                                : "0.00") + taxCode;

                        int totalWidth    = TSIZE22;
                        int rightColWidth = 10;
                        int midColWidth   = 8;
                        int leftColWidth  = totalWidth - midColWidth - rightColWidth;

                        String line2 = String.format(
                                "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                                rateCol,
                                qtyCol,
                                amountCol
                        );

                        mUsbThermalPrinter.addString(line2);
                        mUsbThermalPrinter.printString();

                        Double discountRate    = item.getDiscount_rate();
                        Double itemTotal       = item.getTotal_amount();
                        Double discountedTotal = item.getDiscounted_total();

                        if (discountRate != null && discountRate < 0
                                && itemTotal != null && itemTotal > 0
                                && discountedTotal != null) {

                            String discountText = "Discount " + discountRate.intValue() + "%";

                            String finalAmountStr = FunUtils.INSTANCE.formatPrintPrice(
                                    Double.toString(discountedTotal));

                            String discountLine = String.format(
                                    "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                    discountText,
                                    finalAmountStr
                            );

                            mUsbThermalPrinter.addString(discountLine);
                            mUsbThermalPrinter.printString();
                        }

                        mUsbThermalPrinter.walkPaper(1);
                    }
                }

                // â”€â”€ TOTALS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                String grandTotal = (data.getGrand_total() != null)
                        ? FunUtils.INSTANCE.formatPrintPrice(data.getGrand_total()) : "0.00";

                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setBold(true);
                String totalLabel = receiptStr(R.string.receipt_label_total) + "(" + currency + "):";
                mUsbThermalPrinter.addString(totalLabel + padLeft(grandTotal, TSIZE26 - totalLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);

                // â”€â”€ TAX SUMMARY LOOP â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                List<CopyReceiptRes.TaxSummery> taxSummaryList = data.getTax_summery();
                if (taxSummaryList != null && !taxSummaryList.isEmpty()) {

                    for (CopyReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                        if (taxSummary.getCode() != null) {
                            String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                            double taxableValue = (taxSummary.getTaxable_value() != null)
                                    ? taxSummary.getTaxable_value() : 0.0;
                            mUsbThermalPrinter.addString(taxLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                    TSIZE22 - taxLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    for (CopyReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                        Double taxAmt = taxSummary.getTax_amount();
                        String code = taxSummary.getCode();
                        if (taxAmt != null && taxAmt != 0.0 && code != null) {
                            String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                            mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmt)),
                                    TSIZE22 - taxAmountLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    String totalTaxAmount = data.getTax_amount();
                    if (totalTaxAmount != null && !totalTaxAmount.equals("0")
                            && !totalTaxAmount.equals("0.00")) {
                        String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                        mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(totalTaxAmount),
                                TSIZE22 - totalTaxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                String grandTotall = FunUtils.INSTANCE.formatPrintPrice(String.valueOf(details.getData().getGrand_total()));
                String paymentLabel = receiptStr(R.string.payment_type_cash).replace(":", "");

                if (!paymentLabel.isEmpty()) {
                    mUsbThermalPrinter.addString(paymentLabel + padLeft(grandTotall, TSIZE22 - paymentLabel.length()));
                    mUsbThermalPrinter.printString();
                }

                // Items count
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                int itemsCount = (data.getReturned_items() != null)
                        ? data.getReturned_items().size() : 0;
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(Integer.toString(itemsCount), TSIZE22 - 6));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ SDC INFORMATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_sdc_info));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

                if (data.getVsdc_reciept() != null && !data.getVsdc_reciept().isEmpty()) {
                    CopyReceiptRes.VsdcReceipt vsdc = data.getVsdc_reciept().get(0);

                    // â”€â”€ CHANGED 2: Reuse properly parsed formattedDate/formattedTime â”€â”€
                    mUsbThermalPrinter.addString(vsdcDateLine.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: SDC ID with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String sdcId = (vsdc.getSdcId() != null) ? vsdc.getSdcId() : "";
                    String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                    mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: Receipt Number (SDC) with padLeft â”€â”€â”€â”€â”€
                    String rcptNo = (vsdc.getRcptNo() != null && vsdc.getRcptNo() != 0)
                            ? String.valueOf(vsdc.getRcptNo()) : "";
                    String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
                    String sdcRcptValue = rcptNo + "/" + vsdc.getTotRcptNo() + " CR";
                    mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                    String intrlData = (vsdc.getIntrlData() != null) ? vsdc.getIntrlData() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + intrlData);
                    mUsbThermalPrinter.printString();

                    String rcptSign = (vsdc.getRcptSign() != null) ? vsdc.getRcptSign() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + rcptSign);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ CHANGED 3: Receipt Number, Date/Time, MRC No after receipt signature â”€â”€
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // Receipt Number
                    String invoiceId = (data.getReturned_invoice_id() != null)
                            ? data.getReturned_invoice_id() : "";
                    String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                    mUsbThermalPrinter.addString(rcptNumLabel + padLeft(invoiceId, TSIZE22 - rcptNumLabel.length()));
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 2: Purchase date properly parsed â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String rawPurchaseDateTime = (data.getReturned_date() != null)
                            ? data.getReturned_date() : "";
                    String purchaseDate = "";
                    String purchaseTime = "";
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                        Date parsedPurchaseDate = inputFormat.parse(rawPurchaseDateTime);
                        if (parsedPurchaseDate != null) {
                            purchaseDate = formatReceiptDate(parsedPurchaseDate);
                            purchaseTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedPurchaseDate);
                        }
                    } catch (ParseException e) {
                        Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                        purchaseDate = rawPurchaseDateTime; // fallback
                    }

                    String purchaseDateLabel = receiptStr(R.string.receipt_label_date) + " " + purchaseDate;
                    String purchaseTimeLabel = receiptStr(R.string.receipt_label_time) + " " + purchaseTime;
                    int purchaseSpaces = TSIZE22 - purchaseDateLabel.length() - purchaseTimeLabel.length();
                    if (purchaseSpaces < 1) purchaseSpaces = 1;
                    StringBuilder purchaseDateLine = new StringBuilder(purchaseDateLabel);
                    for (int i = 0; i < purchaseSpaces; i++) purchaseDateLine.append(" ");
                    purchaseDateLine.append(purchaseTimeLabel);

                    mUsbThermalPrinter.addString(purchaseDateLine.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: MRC No with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
                    String mrcValue = (vsdc.getMrcNo() != null) ? vsdc.getMrcNo() + "." : ".";
                    mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
                    mUsbThermalPrinter.printString();
                }

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ FOOTER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_appreciate_biz));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.addString("*** END ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.addString(" ");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.reset();

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("PrinterUtil", "Print error: " + e.getMessage(), e);
                Result = e.toString();
                if (Result.contains("NoPaperException")) {
                    nopaper = true;
                } else if (Result.contains("OverHeatException")) {
                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                } else {
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }
            } finally {
                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                if (nopaper) {
                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                    nopaper = false;
                    return;
                }
            }


        } else if (receiptType.equalsIgnoreCase("zra")) {

        } else if (receiptType.equalsIgnoreCase("tra")) {

        } else if (receiptType.equalsIgnoreCase("moz")) {

        } else if (receiptType.equalsIgnoreCase("type6")) {

        }
    }


    public void printSaleReceipt(SaleReceiptRes details) {
        Log.e("PrinterUtil_Debug", "printSaleReceipt() called. details=" + (details != null ? "non-null" : "NULL"));
        String receiptType;
        try {
            com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper orgHelper =
                    new com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper(context);
            receiptType = orgHelper.getOrganisationData().getReciept_type();
            if (receiptType == null || receiptType.trim().isEmpty()) {
                receiptType = "rra";
            }
        } catch (Exception ex) {
            Log.e("PrinterUtil_Debug", "printSaleReceipt(): failed to read receiptType, defaulting to rra", ex);
            receiptType = "rra";
        }
        Log.e("PrinterUtil_Debug", "printSaleReceipt(): receiptType=" + receiptType);

        if (receiptType.equalsIgnoreCase("rra")) {
            try {
                if (details == null || details.getData() == null) {
                Log.e("PrinterUtil", "Sale receipt data is null");
                handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                return;
            }

            SaleReceiptRes.Data data = details.getData();

            // â”€â”€ HEADER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setLeftIndent(1);
            mUsbThermalPrinter.setLineSpace(3);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("CIS Version : 1.0.1");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(3);

            // Logo
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.image22);
            logoBitmap = Bitmap.createScaledBitmap(logoBitmap, 400, 200, true);
            mUsbThermalPrinter.printLogo(logoBitmap, false);
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ STORE INFO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(32);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(true);
            String storeName = (data.getStore() != null && data.getStore().getStore_name() != null)
                    ? data.getStore().getStore_name().toUpperCase() : "";
            mUsbThermalPrinter.addString(storeName);
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(26);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            String storeAddress = (data.getStore() != null && data.getStore().getAddress() != null)
                    ? data.getStore().getAddress().toUpperCase() : "";
            mUsbThermalPrinter.addString(storeAddress);
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(24);
            mUsbThermalPrinter.setGray(6);
            String tpinNo = (data.getTpin_no() != null) ? data.getTpin_no() : "";
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) + " " + tpinNo);
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ RECEIPT TYPE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.setTextSize(24);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.setAlgin(0);
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ CHANGED 1: Buyer info with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            String buyerName = (data.getCustomer_name() != null) ? data.getCustomer_name() : "";
            String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
            mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
            mUsbThermalPrinter.printString();

            String buyerTin = (data.getBuyers_tpin() != null) ? data.getBuyers_tpin() : "";
            String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
            mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
            mUsbThermalPrinter.printString();

            String buyerContact = (data.getCustomer_mob_no() != null) ? data.getCustomer_mob_no() : "";
            String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
            mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ ITEMS LOOP â€” uses salesItem â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (data.getSalesItem() != null) {
                for (SaleReceiptRes.SalesItem item : data.getSalesItem()) {
                    if (item == null) continue;

                    String productName = (item.getProduct_name() != null)
                            ? item.getProduct_name() : "";
                    numberOfItems++;

                    boolean looseOil = productName.toLowerCase().startsWith("bulk oil");

                    // Line 1: Product Name (bold)
                    mUsbThermalPrinter.setTextSize(24);
                    mUsbThermalPrinter.setGray(6);
                    mUsbThermalPrinter.setBold(true);
                    mUsbThermalPrinter.addString(productName);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setBold(false);
                    mUsbThermalPrinter.setTextSize(22);

                    String taxCode = "";
                    if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                        taxCode = item.getTax_details().getCode();
                    }

                    String rateCol = FunUtils.INSTANCE.formatPrintPrice(
                            item.getTax_inclusive_price() != null
                                    ? String.valueOf(item.getTax_inclusive_price()) : "0.00") + "x";

                    String qtyCol = FunUtils.INSTANCE.DtoString(
                            item.getQuantity() != null ? item.getQuantity() : 0.0);

                    String amountCol = FunUtils.INSTANCE.formatPrintPrice(
                            item.getTotal_amount() != null
                                    ? String.valueOf(item.getTotal_amount()) : "0.00") + taxCode;

                    int totalWidth    = TSIZE22;
                    int rightColWidth = 10;
                    int midColWidth   = 8;
                    int leftColWidth  = totalWidth - midColWidth - rightColWidth;

                    String line2 = String.format(
                            "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                            rateCol,
                            qtyCol,
                            amountCol
                    );

                    mUsbThermalPrinter.addString(line2);
                    mUsbThermalPrinter.printString();

                    Double discountRate = item.getDiscount_rate();
                    Double totalAmount  = item.getTotal_amount();

                    if (discountRate != null && discountRate < 0
                            && totalAmount != null && totalAmount > 0) {

                        String discountText = "Discount " + discountRate.intValue() + "%";

                        double discountedTotal = totalAmount + (totalAmount * discountRate / 100);

                        String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(
                                String.valueOf(discountedTotal));

                        String discountLine = String.format(
                                "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                discountText,
                                discountAmountStr
                        );

                        mUsbThermalPrinter.addString(discountLine);
                        mUsbThermalPrinter.printString();
                    }

                    mUsbThermalPrinter.walkPaper(1);
                }
            }
                mUsbThermalPrinter.setTextSize(22);
                String spotAmount = data.getSpot_discount_amount();
                if (hasSpotDiscount(spotAmount)) {
                    String pct = data.getSpot_discount_percentage();
                    String pctVal = (pct != null && !pct.isEmpty()) ? pct : "0";
                String sptdiscount = receiptStr(R.string.print_on_spot_discount_label, pctVal);
                    mUsbThermalPrinter.addString(sptdiscount + padLeft(FunUtils.INSTANCE.formatPrintPrice(spotAmount), TSIZE22 - sptdiscount.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                } else {
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }

            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setAlgin(1);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.setTextSize(23);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();

            // â”€â”€ TOTALS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


            String grandTotal = (data.getGrand_total() != null)
                    ? FunUtils.INSTANCE.formatPrintPrice(data.getGrand_total()) : "0.00";
            mUsbThermalPrinter.setTextSize(26);
            mUsbThermalPrinter.setBold(true);
            String totalLabel = receiptStr(R.string.receipt_label_total) + "(" + currency + "):";
            mUsbThermalPrinter.addString(totalLabel + padLeft(grandTotal, TSIZE26 - totalLabel.length()));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);

            // â”€â”€ TAX SUMMARY LOOP â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            List<SaleReceiptRes.TaxSummery> taxSummaryList = data.getTax_summery();
            if (taxSummaryList != null && !taxSummaryList.isEmpty()) {

                for (SaleReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                    if (taxSummary.getCode() != null) {
                        String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                        double taxableValue = (taxSummary.getTaxable_value() != null)
                                ? taxSummary.getTaxable_value() : 0.0;
                        mUsbThermalPrinter.addString(taxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                TSIZE22 - taxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                for (SaleReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                    Double taxAmt = taxSummary.getTax_amount();
                    String code = taxSummary.getCode();
                    if (taxAmt != null && taxAmt != 0.0 && code != null) {
                        String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                        mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmt)),
                                TSIZE22 - taxAmountLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                String totalTaxAmount = data.getTax_amount();
                if (totalTaxAmount != null && !totalTaxAmount.equals("0")
                        && !totalTaxAmount.equals("0.00")) {
                    String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                    mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                            FunUtils.INSTANCE.formatPrintPrice(totalTaxAmount),
                            TSIZE22 - totalTaxLabel.length()
                    ));
                    mUsbThermalPrinter.printString();
                }
            }

            mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // Items count
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            int itemsCount = (data.getSalesItem() != null) ? data.getSalesItem().size() : 0;
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(Integer.toString(itemsCount), TSIZE22 - 6));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            String rcptTypeRaw = receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault());
            mUsbThermalPrinter.setTextSize(24);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.addString(rcptTypeRaw);
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ SDC INFORMATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setBold(true);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_sdc_info));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

            if (data.getVsdc_reciept() != null && !data.getVsdc_reciept().isEmpty()) {
                SaleReceiptRes.VsdcReceipt vsdc = data.getVsdc_reciept().get(0);

                // â”€â”€ CHANGED 2: Date parsed properly with yyyyMMddHHmmss â”€â”€
                String vsdcRawDate = (vsdc.getVsdcRcptPbctDate() != null)
                        ? vsdc.getVsdcRcptPbctDate() : "";
                String vsdcDate = "";
                String vsdcTime = "";
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                    Date parsedVsdcDate = inputFormat.parse(vsdcRawDate);
                    if (parsedVsdcDate != null) {
                        vsdcDate = formatReceiptDate(parsedVsdcDate);
                        vsdcTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedVsdcDate);
                    }
                } catch (ParseException e) {
                    Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                    vsdcDate = vsdcRawDate; // fallback
                }

                String vsdcDateLabel = receiptStr(R.string.receipt_label_date) + " " + vsdcDate;
                String vsdcTimeLabel = receiptStr(R.string.receipt_label_time) + " " + vsdcTime;
                int vsdcSpaces = TSIZE22 - vsdcDateLabel.length() - vsdcTimeLabel.length();
                if (vsdcSpaces < 1) vsdcSpaces = 1;
                StringBuilder vsdcDateLine = new StringBuilder(vsdcDateLabel);
                for (int i = 0; i < vsdcSpaces; i++) vsdcDateLine.append(" ");
                vsdcDateLine.append(vsdcTimeLabel);

                mUsbThermalPrinter.addString(vsdcDateLine.toString());
                mUsbThermalPrinter.printString();

                // â”€â”€ CHANGED 1: SDC ID with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String sdcId = (vsdc.getSdcId() != null) ? vsdc.getSdcId() : "";
                String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                mUsbThermalPrinter.printString();

                // â”€â”€ CHANGED 1: Receipt Number (SDC) with padLeft â”€â”€â”€â”€â”€
                String rcptNo = (vsdc.getRcptNo() != null && vsdc.getRcptNo() != 0)
                        ? String.valueOf(vsdc.getRcptNo()) : "";
                String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
                String sdcRcptValue = rcptNo + "/" + vsdc.getTotRcptNo() + " CS";
                mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                String intrlData = (vsdc.getIntrlData() != null) ? vsdc.getIntrlData() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + intrlData);
                mUsbThermalPrinter.printString();

                String rcptSign = (vsdc.getRcptSign() != null) ? vsdc.getRcptSign() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + rcptSign);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ RECEIPT NUMBER / DATE / MRC NO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ CHANGED 1: Receipt Number with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String receiptNum = (data.getInvoice_id() != null) ? data.getInvoice_id() : "";
                String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
                mUsbThermalPrinter.printString();

                // â”€â”€ CHANGED 2: Purchase date properly parsed â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String rawPurchaseDateTime = (data.getPurchase_date_time() != null)
                        ? data.getPurchase_date_time() : "";
                String purchaseDate = "";
                String purchaseTime = "";
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                    Date parsedDate = inputFormat.parse(rawPurchaseDateTime);
                    if (parsedDate != null) {
                        purchaseDate = formatReceiptDate(parsedDate);
                        purchaseTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(parsedDate);
                    }
                } catch (ParseException e) {
                    Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                    purchaseDate = rawPurchaseDateTime; // fallback
                }

                String purchaseDateLabel = receiptStr(R.string.receipt_label_date) + " " + purchaseDate;
                String purchaseTimeLabel = receiptStr(R.string.receipt_label_time) + " " + purchaseTime;
                int purchaseSpaces = TSIZE22 - purchaseDateLabel.length() - purchaseTimeLabel.length();
                if (purchaseSpaces < 1) purchaseSpaces = 1;
                StringBuilder purchaseDateLine = new StringBuilder(purchaseDateLabel);
                for (int i = 0; i < purchaseSpaces; i++) purchaseDateLine.append(" ");
                purchaseDateLine.append(purchaseTimeLabel);

                mUsbThermalPrinter.addString(purchaseDateLine.toString());
                mUsbThermalPrinter.printString();

                // â”€â”€ CHANGED 1: MRC No with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
                String mrcValue = (vsdc.getMrcNo() != null) ? vsdc.getMrcNo() + "." : ".";
                mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
                mUsbThermalPrinter.printString();

            } else {

                String sdcId = (data.getSdc_id() != null) ? data.getSdc_id() : "";
                String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                mUsbThermalPrinter.printString();

                String receiptNo = (data.getReceipt_no() != null) ? data.getReceipt_no() : "";
                String fallbackRcptLabel = context.getString(R.string.receipt_label_receipt_no);
                mUsbThermalPrinter.addString(fallbackRcptLabel + padLeft(receiptNo + " NS", TSIZE22 - fallbackRcptLabel.length()));
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                String internalData = (data.getInternal_data() != null) ? data.getInternal_data() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + internalData);
                mUsbThermalPrinter.printString();

                String receiptSign = (data.getReceipt_sign() != null) ? data.getReceipt_sign() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + receiptSign);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            }

            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.addString("----------------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            // â”€â”€ FOOTER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setBold(false);
            mUsbThermalPrinter.setGray(6);
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_appreciate_biz));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(3);

            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.addString("*** END ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(5);
            mUsbThermalPrinter.addString(" ");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(5);
            mUsbThermalPrinter.reset();

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("PrinterUtil", "Print error: " + e.getMessage(), e);
            Result = e.toString();
            if (Result.contains("NoPaperException")) {
                nopaper = true;
            } else if (Result.contains("OverHeatException")) {
                handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
            } else {
                handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
            }
        } finally {
            handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
            if (nopaper) {
                handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                nopaper = false;
                return;
            }
        }
        } else if (receiptType.equalsIgnoreCase("default")) {
            try {
                if (details == null || details.getData() == null) {
                    Log.e("PrinterUtil", "Sale receipt data is null");
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                    return;
                }

                SaleReceiptRes.Data data = details.getData();

                // â”€â”€ HEADER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setLeftIndent(1);
                mUsbThermalPrinter.setLineSpace(3);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("CIS Version : 1.0.1");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                // Logo
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.image22);
                logoBitmap = Bitmap.createScaledBitmap(logoBitmap, 400, 200, true);
//                mUsbThermalPrinter.printLogo(logoBitmap, false);
//                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ STORE INFO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(32);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);
                String storeName = (data.getStore() != null && data.getStore().getStore_name() != null)
                        ? data.getStore().getStore_name().toUpperCase() : "";
                mUsbThermalPrinter.addString(storeName);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                String storeAddress = (data.getStore() != null && data.getStore().getAddress() != null)
                        ? data.getStore().getAddress().toUpperCase() : "";
                mUsbThermalPrinter.addString(storeAddress);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setGray(6);
                String tpinNo = (data.getTpin_no() != null) ? data.getTpin_no() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) + " " + tpinNo);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ RECEIPT TYPE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setAlgin(0);
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ CHANGED 1: Buyer info with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String buyerName = (data.getCustomer_name() != null) ? data.getCustomer_name() : "";
                String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
                mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerTin = (data.getBuyers_tpin() != null) ? data.getBuyers_tpin() : "";
                String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
                mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerContact = (data.getCustomer_mob_no() != null) ? data.getCustomer_mob_no() : "";
                String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
                mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ ITEMS LOOP â€” uses salesItem â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                if (data.getSalesItem() != null) {
                    for (SaleReceiptRes.SalesItem item : data.getSalesItem()) {
                        if (item == null) continue;

                        String productName = (item.getProduct_name() != null)
                                ? item.getProduct_name() : "";
                        numberOfItems++;

                        boolean looseOil = productName.toLowerCase().startsWith("bulk oil");

                        // Line 1: Product Name (bold)
                        mUsbThermalPrinter.setTextSize(24);
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.addString(productName);
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);

                        String taxCode = "";
                        if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                            taxCode = item.getTax_details().getCode();
                        }

                        String rateCol = FunUtils.INSTANCE.formatPrintPrice(
                                item.getTax_inclusive_price() != null
                                        ? String.valueOf(item.getTax_inclusive_price()) : "0.00") + "x";

                        String qtyCol = FunUtils.INSTANCE.DtoString(
                                item.getQuantity() != null ? item.getQuantity() : 0.0);

                        String amountCol = FunUtils.INSTANCE.formatPrintPrice(
                                item.getTotal_amount() != null
                                        ? String.valueOf(item.getTotal_amount()) : "0.00") + taxCode;

                        int totalWidth    = TSIZE22;
                        int rightColWidth = 10;
                        int midColWidth   = 8;
                        int leftColWidth  = totalWidth - midColWidth - rightColWidth;

                        String line2 = String.format(
                                "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                                rateCol,
                                qtyCol,
                                amountCol
                        );

                        mUsbThermalPrinter.addString(line2);
                        mUsbThermalPrinter.printString();

                        Double discountRate = item.getDiscount_rate();
                        Double totalAmount  = item.getTotal_amount();

                        if (discountRate != null && discountRate < 0
                                && totalAmount != null && totalAmount > 0) {

                            String discountText = "Discount " + discountRate.intValue() + "%";

                            double discountedTotal = totalAmount + (totalAmount * discountRate / 100);

                            String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(
                                    String.valueOf(discountedTotal));

                            String discountLine = String.format(
                                    "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                    discountText,
                                    discountAmountStr
                            );

                            mUsbThermalPrinter.addString(discountLine);
                            mUsbThermalPrinter.printString();
                        }

                        mUsbThermalPrinter.walkPaper(1);
                    }
                }
                mUsbThermalPrinter.setTextSize(22);
                String spotAmount = data.getSpot_discount_amount();
                if (hasSpotDiscount(spotAmount)) {
                    String pct = data.getSpot_discount_percentage();
                    String pctVal = (pct != null && !pct.isEmpty()) ? pct : "0";
                String sptdiscount = receiptStr(R.string.print_on_spot_discount_label, pctVal);
                    mUsbThermalPrinter.addString(sptdiscount + padLeft(FunUtils.INSTANCE.formatPrintPrice(spotAmount), TSIZE22 - sptdiscount.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                } else {
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }

                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();

                // â”€â”€ TOTALS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


                String grandTotal = (data.getGrand_total() != null)
                        ? FunUtils.INSTANCE.formatPrintPrice(data.getGrand_total()) : "0.00";
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setBold(true);
                String totalLabel = receiptStr(R.string.receipt_label_total) + "(" + currency + "):";
                mUsbThermalPrinter.addString(totalLabel + padLeft(grandTotal, TSIZE26 - totalLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);

                // â”€â”€ TAX SUMMARY LOOP â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                List<SaleReceiptRes.TaxSummery> taxSummaryList = data.getTax_summery();
                if (taxSummaryList != null && !taxSummaryList.isEmpty()) {

                    for (SaleReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                        if (taxSummary.getCode() != null) {
                            String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                            double taxableValue = (taxSummary.getTaxable_value() != null)
                                    ? taxSummary.getTaxable_value() : 0.0;
                            mUsbThermalPrinter.addString(taxLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                    TSIZE22 - taxLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    for (SaleReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                        Double taxAmt = taxSummary.getTax_amount();
                        String code = taxSummary.getCode();
                        if (taxAmt != null && taxAmt != 0.0 && code != null) {
                            String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                            mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmt)),
                                    TSIZE22 - taxAmountLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    String totalTaxAmount = data.getTax_amount();
                    if (totalTaxAmount != null && !totalTaxAmount.equals("0")
                            && !totalTaxAmount.equals("0.00")) {
                        String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                        mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(totalTaxAmount),
                                TSIZE22 - totalTaxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // Items count
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                int itemsCount = (data.getSalesItem() != null) ? data.getSalesItem().size() : 0;
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(Integer.toString(itemsCount), TSIZE22 - 6));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                String rcptTypeRaw = receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault());
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(rcptTypeRaw);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ SDC INFORMATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_sdc_info));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

                if (data.getVsdc_reciept() != null && !data.getVsdc_reciept().isEmpty()) {
                    SaleReceiptRes.VsdcReceipt vsdc = data.getVsdc_reciept().get(0);

                    // â”€â”€ CHANGED 2: Date parsed properly with yyyyMMddHHmmss â”€â”€
                    String vsdcRawDate = (vsdc.getVsdcRcptPbctDate() != null)
                            ? vsdc.getVsdcRcptPbctDate() : "";
                    String vsdcDate = "";
                    String vsdcTime = "";
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                        Date parsedVsdcDate = inputFormat.parse(vsdcRawDate);
                        if (parsedVsdcDate != null) {
                            vsdcDate = formatReceiptDate(parsedVsdcDate);
                            vsdcTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedVsdcDate);
                        }
                    } catch (ParseException e) {
                        Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                        vsdcDate = vsdcRawDate; // fallback
                    }

                    String vsdcDateLabel = receiptStr(R.string.receipt_label_date) + " " + vsdcDate;
                    String vsdcTimeLabel = receiptStr(R.string.receipt_label_time) + " " + vsdcTime;
                    int vsdcSpaces = TSIZE22 - vsdcDateLabel.length() - vsdcTimeLabel.length();
                    if (vsdcSpaces < 1) vsdcSpaces = 1;
                    StringBuilder vsdcDateLine = new StringBuilder(vsdcDateLabel);
                    for (int i = 0; i < vsdcSpaces; i++) vsdcDateLine.append(" ");
                    vsdcDateLine.append(vsdcTimeLabel);

                    mUsbThermalPrinter.addString(vsdcDateLine.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: SDC ID with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String sdcId = (vsdc.getSdcId() != null) ? vsdc.getSdcId() : "";
                    String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                    mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: Receipt Number (SDC) with padLeft â”€â”€â”€â”€â”€
                    String rcptNo = (vsdc.getRcptNo() != null && vsdc.getRcptNo() != 0)
                            ? String.valueOf(vsdc.getRcptNo()) : "";
                    String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
                    String sdcRcptValue = rcptNo + "/" + vsdc.getTotRcptNo() + " CS";
                    mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                    String intrlData = (vsdc.getIntrlData() != null) ? vsdc.getIntrlData() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + intrlData);
                    mUsbThermalPrinter.printString();

                    String rcptSign = (vsdc.getRcptSign() != null) ? vsdc.getRcptSign() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + rcptSign);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ RECEIPT NUMBER / DATE / MRC NO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ CHANGED 1: Receipt Number with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String receiptNum = (data.getInvoice_id() != null) ? data.getInvoice_id() : "";
                    String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                    mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 2: Purchase date properly parsed â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String rawPurchaseDateTime = (data.getPurchase_date_time() != null)
                            ? data.getPurchase_date_time() : "";
                    String purchaseDate = "";
                    String purchaseTime = "";
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                        Date parsedDate = inputFormat.parse(rawPurchaseDateTime);
                        if (parsedDate != null) {
                            purchaseDate = formatReceiptDate(parsedDate);
                            purchaseTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(parsedDate);
                        }
                    } catch (ParseException e) {
                        Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                        purchaseDate = rawPurchaseDateTime; // fallback
                    }

                    String purchaseDateLabel = receiptStr(R.string.receipt_label_date) + " " + purchaseDate;
                    String purchaseTimeLabel = receiptStr(R.string.receipt_label_time) + " " + purchaseTime;
                    int purchaseSpaces = TSIZE22 - purchaseDateLabel.length() - purchaseTimeLabel.length();
                    if (purchaseSpaces < 1) purchaseSpaces = 1;
                    StringBuilder purchaseDateLine = new StringBuilder(purchaseDateLabel);
                    for (int i = 0; i < purchaseSpaces; i++) purchaseDateLine.append(" ");
                    purchaseDateLine.append(purchaseTimeLabel);

                    mUsbThermalPrinter.addString(purchaseDateLine.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: MRC No with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
                    String mrcValue = (vsdc.getMrcNo() != null) ? vsdc.getMrcNo() + "." : ".";
                    mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
                    mUsbThermalPrinter.printString();

                } else {

                    String sdcId = (data.getSdc_id() != null) ? data.getSdc_id() : "";
                    String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                    mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                    mUsbThermalPrinter.printString();

                    String receiptNo = (data.getReceipt_no() != null) ? data.getReceipt_no() : "";
                    String fallbackRcptLabel = context.getString(R.string.receipt_label_receipt_no);
                    mUsbThermalPrinter.addString(fallbackRcptLabel + padLeft(receiptNo + " NS", TSIZE22 - fallbackRcptLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                    String internalData = (data.getInternal_data() != null) ? data.getInternal_data() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + internalData);
                    mUsbThermalPrinter.printString();

                    String receiptSign = (data.getReceipt_sign() != null) ? data.getReceipt_sign() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + receiptSign);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ FOOTER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_appreciate_biz));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.addString("*** END ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.addString(" ");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.reset();

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("PrinterUtil", "Print error: " + e.getMessage(), e);
                Result = e.toString();
                if (Result.contains("NoPaperException")) {
                    nopaper = true;
                } else if (Result.contains("OverHeatException")) {
                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                } else {
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }
            } finally {
                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                if (nopaper) {
                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                    nopaper = false;
                    return;
                }
            }

        } else if (receiptType.equalsIgnoreCase("zra")) {
            try {
                if (details == null || details.getData() == null) {
                    Log.e("PrinterUtil", "Sale receipt data is null");
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                    return;
                }

                SaleReceiptRes.Data data = details.getData();

                // â”€â”€ HEADER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setLeftIndent(1);
                mUsbThermalPrinter.setLineSpace(3);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("CIS Version : 1.0.1");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                // Logo
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.image22);
                logoBitmap = Bitmap.createScaledBitmap(logoBitmap, 400, 200, true);
                mUsbThermalPrinter.printLogo(logoBitmap, false);
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ STORE INFO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(32);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);
                String storeName = (data.getStore() != null && data.getStore().getStore_name() != null)
                        ? data.getStore().getStore_name().toUpperCase() : "";
                mUsbThermalPrinter.addString(storeName);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                String storeAddress = (data.getStore() != null && data.getStore().getAddress() != null)
                        ? data.getStore().getAddress().toUpperCase() : "";
                mUsbThermalPrinter.addString(storeAddress);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setGray(6);
                String tpinNo = (data.getTpin_no() != null) ? data.getTpin_no() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) + " " + tpinNo);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ RECEIPT TYPE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setAlgin(0);
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ CHANGED 1: Buyer info with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String buyerName = (data.getCustomer_name() != null) ? data.getCustomer_name() : "";
                String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
                mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerTin = (data.getBuyers_tpin() != null) ? data.getBuyers_tpin() : "";
                String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
                mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerContact = (data.getCustomer_mob_no() != null) ? data.getCustomer_mob_no() : "";
                String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
                mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ ITEMS LOOP â€” uses salesItem â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                if (data.getSalesItem() != null) {
                    for (SaleReceiptRes.SalesItem item : data.getSalesItem()) {
                        if (item == null) continue;

                        String productName = (item.getProduct_name() != null)
                                ? item.getProduct_name() : "";
                        numberOfItems++;

                        boolean looseOil = productName.toLowerCase().startsWith("bulk oil");

                        // Line 1: Product Name (bold)
                        mUsbThermalPrinter.setTextSize(24);
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.addString(productName);
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);

                        String taxCode = "";
                        if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                            taxCode = item.getTax_details().getCode();
                        }

                        String rateCol = FunUtils.INSTANCE.formatPrintPrice(
                                item.getTax_inclusive_price() != null
                                        ? String.valueOf(item.getTax_inclusive_price()) : "0.00") + "x";

                        String qtyCol = FunUtils.INSTANCE.DtoString(
                                item.getQuantity() != null ? item.getQuantity() : 0.0);

                        String amountCol = FunUtils.INSTANCE.formatPrintPrice(
                                item.getTotal_amount() != null
                                        ? String.valueOf(item.getTotal_amount()) : "0.00") + taxCode;

                        int totalWidth    = TSIZE22;
                        int rightColWidth = 10;
                        int midColWidth   = 8;
                        int leftColWidth  = totalWidth - midColWidth - rightColWidth;

                        String line2 = String.format(
                                "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                                rateCol,
                                qtyCol,
                                amountCol
                        );

                        mUsbThermalPrinter.addString(line2);
                        mUsbThermalPrinter.printString();

                        Double discountRate = item.getDiscount_rate();
                        Double totalAmount  = item.getTotal_amount();

                        if (discountRate != null && discountRate < 0
                                && totalAmount != null && totalAmount > 0) {

                            String discountText = "Discount " + discountRate.intValue() + "%";

                            double discountedTotal = totalAmount + (totalAmount * discountRate / 100);

                            String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(
                                    String.valueOf(discountedTotal));

                            String discountLine = String.format(
                                    "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                    discountText,
                                    discountAmountStr
                            );

                            mUsbThermalPrinter.addString(discountLine);
                            mUsbThermalPrinter.printString();
                        }

                        mUsbThermalPrinter.walkPaper(1);
                    }
                }
                mUsbThermalPrinter.setTextSize(22);
                String spotAmount = data.getSpot_discount_amount();
                if (hasSpotDiscount(spotAmount)) {
                    String pct = data.getSpot_discount_percentage();
                    String pctVal = (pct != null && !pct.isEmpty()) ? pct : "0";
                String sptdiscount = receiptStr(R.string.print_on_spot_discount_label, pctVal);
                    mUsbThermalPrinter.addString(sptdiscount + padLeft(FunUtils.INSTANCE.formatPrintPrice(spotAmount), TSIZE22 - sptdiscount.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }else {
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.setGray(6);
                }

                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);

                // â”€â”€ TOTALS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);


                String grandTotal = (data.getGrand_total() != null)
                        ? FunUtils.INSTANCE.formatPrintPrice(data.getGrand_total()) : "0.00";
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setBold(true);
                String totalLabel = receiptStr(R.string.receipt_label_total) + "(" + currency + "):";
                mUsbThermalPrinter.addString(totalLabel + padLeft(grandTotal, TSIZE26 - totalLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);

                // â”€â”€ TAX SUMMARY LOOP â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                List<SaleReceiptRes.TaxSummery> taxSummaryList = data.getTax_summery();
                if (taxSummaryList != null && !taxSummaryList.isEmpty()) {

                    for (SaleReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                        if (taxSummary.getCode() != null) {
                            String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                            double taxableValue = (taxSummary.getTaxable_value() != null)
                                    ? taxSummary.getTaxable_value() : 0.0;
                            mUsbThermalPrinter.addString(taxLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                    TSIZE22 - taxLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    for (SaleReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                        Double taxAmt = taxSummary.getTax_amount();
                        String code = taxSummary.getCode();
                        if (taxAmt != null && taxAmt != 0.0 && code != null) {
                            String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                            mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmt)),
                                    TSIZE22 - taxAmountLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    String totalTaxAmount = data.getTax_amount();
                    if (totalTaxAmount != null && !totalTaxAmount.equals("0")
                            && !totalTaxAmount.equals("0.00")) {
                        String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                        mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(totalTaxAmount),
                                TSIZE22 - totalTaxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // Items count
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                int itemsCount = (data.getSalesItem() != null) ? data.getSalesItem().size() : 0;
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(Integer.toString(itemsCount), TSIZE22 - 6));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                String rcptTypeRaw = receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault());
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(rcptTypeRaw);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ SDC INFORMATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_sdc_info));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

                if (data.getVsdc_reciept() != null && !data.getVsdc_reciept().isEmpty()) {
                    SaleReceiptRes.VsdcReceipt vsdc = data.getVsdc_reciept().get(0);

                    // â”€â”€ CHANGED 2: Date parsed properly with yyyyMMddHHmmss â”€â”€
                    String vsdcRawDate = (vsdc.getVsdcRcptPbctDate() != null)
                            ? vsdc.getVsdcRcptPbctDate() : "";
                    String vsdcDate = "";
                    String vsdcTime = "";
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                        Date parsedVsdcDate = inputFormat.parse(vsdcRawDate);
                        if (parsedVsdcDate != null) {
                            vsdcDate = formatReceiptDate(parsedVsdcDate);
                            vsdcTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedVsdcDate);
                        }
                    } catch (ParseException e) {
                        Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                        vsdcDate = vsdcRawDate; // fallback
                    }

                    String vsdcDateLabel = receiptStr(R.string.receipt_label_date) + " " + vsdcDate;
                    String vsdcTimeLabel = receiptStr(R.string.receipt_label_time) + " " + vsdcTime;
                    int vsdcSpaces = TSIZE22 - vsdcDateLabel.length() - vsdcTimeLabel.length();
                    if (vsdcSpaces < 1) vsdcSpaces = 1;
                    StringBuilder vsdcDateLine = new StringBuilder(vsdcDateLabel);
                    for (int i = 0; i < vsdcSpaces; i++) vsdcDateLine.append(" ");
                    vsdcDateLine.append(vsdcTimeLabel);

                    mUsbThermalPrinter.addString(vsdcDateLine.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: SDC ID with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String sdcId = (vsdc.getSdcId() != null) ? vsdc.getSdcId() : "";
                    String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                    mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: Receipt Number (SDC) with padLeft â”€â”€â”€â”€â”€
                    String rcptNo = (vsdc.getRcptNo() != null && vsdc.getRcptNo() != 0)
                            ? String.valueOf(vsdc.getRcptNo()) : "";
                    String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
                    String sdcRcptValue = rcptNo + "/" + vsdc.getTotRcptNo() + " CS";
                    mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                    String intrlData = (vsdc.getIntrlData() != null) ? vsdc.getIntrlData() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + intrlData);
                    mUsbThermalPrinter.printString();

                    String rcptSign = (vsdc.getRcptSign() != null) ? vsdc.getRcptSign() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + rcptSign);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ RECEIPT NUMBER / DATE / MRC NO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ CHANGED 1: Receipt Number with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String receiptNum = (data.getInvoice_id() != null) ? data.getInvoice_id() : "";
                    String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                    mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 2: Purchase date properly parsed â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String rawPurchaseDateTime = (data.getPurchase_date_time() != null)
                            ? data.getPurchase_date_time() : "";
                    String purchaseDate = "";
                    String purchaseTime = "";
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                        Date parsedDate = inputFormat.parse(rawPurchaseDateTime);
                        if (parsedDate != null) {
                            purchaseDate = formatReceiptDate(parsedDate);
                            purchaseTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(parsedDate);
                        }
                    } catch (ParseException e) {
                        Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                        purchaseDate = rawPurchaseDateTime; // fallback
                    }

                    String purchaseDateLabel = receiptStr(R.string.receipt_label_date) + " " + purchaseDate;
                    String purchaseTimeLabel = receiptStr(R.string.receipt_label_time) + " " + purchaseTime;
                    int purchaseSpaces = TSIZE22 - purchaseDateLabel.length() - purchaseTimeLabel.length();
                    if (purchaseSpaces < 1) purchaseSpaces = 1;
                    StringBuilder purchaseDateLine = new StringBuilder(purchaseDateLabel);
                    for (int i = 0; i < purchaseSpaces; i++) purchaseDateLine.append(" ");
                    purchaseDateLine.append(purchaseTimeLabel);

                    mUsbThermalPrinter.addString(purchaseDateLine.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: MRC No with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
                    String mrcValue = (vsdc.getMrcNo() != null) ? vsdc.getMrcNo() + "." : ".";
                    mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
                    mUsbThermalPrinter.printString();

                } else {

                    String sdcId = (data.getSdc_id() != null) ? data.getSdc_id() : "";
                    String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                    mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                    mUsbThermalPrinter.printString();

                    String receiptNo = (data.getReceipt_no() != null) ? data.getReceipt_no() : "";
                    String fallbackRcptLabel = context.getString(R.string.receipt_label_receipt_no);
                    mUsbThermalPrinter.addString(fallbackRcptLabel + padLeft(receiptNo + " CS", TSIZE22 - fallbackRcptLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                    String internalData = (data.getInternal_data() != null) ? data.getInternal_data() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + internalData);
                    mUsbThermalPrinter.printString();

                    String receiptSign = (data.getReceipt_sign() != null) ? data.getReceipt_sign() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + receiptSign);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ FOOTER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_appreciate_biz));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.addString("*** END ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.addString(" ");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.reset();

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("PrinterUtil", "Print error: " + e.getMessage(), e);
                Result = e.toString();
                if (Result.contains("NoPaperException")) {
                    nopaper = true;
                } else if (Result.contains("OverHeatException")) {
                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                } else {
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }
            } finally {
                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                if (nopaper) {
                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                    nopaper = false;
                    return;
                }
            }

        } else if (receiptType.equalsIgnoreCase("tra")) {
            try {
                if (details == null || details.getData() == null) {
                    Log.e("PrinterUtil", "Sale receipt data is null");
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                    return;
                }

                SaleReceiptRes.Data data = details.getData();

                // â”€â”€ HEADER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setLeftIndent(1);
                mUsbThermalPrinter.setLineSpace(3);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("*** START OF LEGAL RECEIPT ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("CIS Version : 1.0.1");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                // Logo
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.image22);
                logoBitmap = Bitmap.createScaledBitmap(logoBitmap, 400, 200, true);
                mUsbThermalPrinter.printLogo(logoBitmap, false);
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ STORE INFO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(32);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(true);
                String storeName = (data.getStore() != null && data.getStore().getStore_name() != null)
                        ? data.getStore().getStore_name().toUpperCase() : "";
                mUsbThermalPrinter.addString(storeName);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                String storeAddress = (data.getStore() != null && data.getStore().getAddress() != null)
                        ? data.getStore().getAddress().toUpperCase() : "";
                mUsbThermalPrinter.addString(storeAddress);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setGray(6);
                String tpinNo = (data.getTpin_no() != null) ? data.getTpin_no() : "";
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_tin_no) + " " + tpinNo);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ RECEIPT TYPE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();

                mUsbThermalPrinter.setAlgin(0);
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ CHANGED 1: Buyer info with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                String buyerName = (data.getCustomer_name() != null) ? data.getCustomer_name() : "";
                String buyerNameLabel = receiptStr(R.string.receipt_label_buyer_name);
                mUsbThermalPrinter.addString(buyerNameLabel + padLeft(buyerName, TSIZE22 - buyerNameLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerTin = (data.getBuyers_tpin() != null) ? data.getBuyers_tpin() : "";
                String buyerTinLabel = receiptStr(R.string.receipt_label_buyer_tin);
                mUsbThermalPrinter.addString(buyerTinLabel + padLeft(buyerTin, TSIZE22 - buyerTinLabel.length()));
                mUsbThermalPrinter.printString();

                String buyerContact = (data.getCustomer_mob_no() != null) ? data.getCustomer_mob_no() : "";
                String buyerContactLabel = receiptStr(R.string.receipt_label_buyer_contact);
                mUsbThermalPrinter.addString(buyerContactLabel + padLeft(buyerContact, TSIZE22 - buyerContactLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ ITEMS LOOP â€” uses salesItem â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                if (data.getSalesItem() != null) {
                    for (SaleReceiptRes.SalesItem item : data.getSalesItem()) {
                        if (item == null) continue;

                        String productName = (item.getProduct_name() != null)
                                ? item.getProduct_name() : "";
                        numberOfItems++;

                        boolean looseOil = productName.toLowerCase().startsWith("bulk oil");

                        // Line 1: Product Name (bold)
                        mUsbThermalPrinter.setTextSize(24);
                        mUsbThermalPrinter.setGray(6);
                        mUsbThermalPrinter.setBold(true);
                        mUsbThermalPrinter.addString(productName);
                        mUsbThermalPrinter.printString();
                        mUsbThermalPrinter.setBold(false);
                        mUsbThermalPrinter.setTextSize(22);

                        String taxCode = "";
                        if (item.getTax_details() != null && item.getTax_details().getCode() != null) {
                            taxCode = item.getTax_details().getCode();
                        }

                        String rateCol = FunUtils.INSTANCE.formatPrintPrice(
                                item.getTax_inclusive_price() != null
                                        ? String.valueOf(item.getTax_inclusive_price()) : "0.00") + "x";

                        String qtyCol = FunUtils.INSTANCE.DtoString(
                                item.getQuantity() != null ? item.getQuantity() : 0.0);

                        String amountCol = FunUtils.INSTANCE.formatPrintPrice(
                                item.getTotal_amount() != null
                                        ? String.valueOf(item.getTotal_amount()) : "0.00") + taxCode;

                        int totalWidth    = TSIZE22;
                        int rightColWidth = 10;
                        int midColWidth   = 8;
                        int leftColWidth  = totalWidth - midColWidth - rightColWidth;

                        String line2 = String.format(
                                "%-" + leftColWidth + "s%" + midColWidth + "s%" + rightColWidth + "s",
                                rateCol,
                                qtyCol,
                                amountCol
                        );

                        mUsbThermalPrinter.addString(line2);
                        mUsbThermalPrinter.printString();

                        Double discountRate = item.getDiscount_rate();
                        Double totalAmount  = item.getTotal_amount();

                        if (discountRate != null && discountRate < 0
                                && totalAmount != null && totalAmount > 0) {

                            String discountText = "Discount " + discountRate.intValue() + "%";

                            double discountedTotal = totalAmount + (totalAmount * discountRate / 100);

                            String discountAmountStr = FunUtils.INSTANCE.formatPrintPrice(
                                    String.valueOf(discountedTotal));

                            String discountLine = String.format(
                                    "%-" + (totalWidth - rightColWidth) + "s%" + rightColWidth + "s",
                                    discountText,
                                    discountAmountStr
                            );

                            mUsbThermalPrinter.addString(discountLine);
                            mUsbThermalPrinter.printString();
                        }

                        mUsbThermalPrinter.walkPaper(1);
                    }
                }
                mUsbThermalPrinter.setTextSize(22);
                String spotAmount = data.getSpot_discount_amount();
                if (hasSpotDiscount(spotAmount)) {
                    String pct = data.getSpot_discount_percentage();
                    String pctVal = (pct != null && !pct.isEmpty()) ? pct : "0";
                String sptdiscount = receiptStr(R.string.print_on_spot_discount_label, pctVal);
                    mUsbThermalPrinter.addString(sptdiscount + padLeft(FunUtils.INSTANCE.formatPrintPrice(spotAmount), TSIZE22 - sptdiscount.length()));
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                } else {
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setAlgin(1);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(23);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_not_official));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ TOTALS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


                String grandTotal = (data.getGrand_total() != null)
                        ? FunUtils.INSTANCE.formatPrintPrice(data.getGrand_total()) : "0.00";
                mUsbThermalPrinter.setTextSize(26);
                mUsbThermalPrinter.setBold(true);
                String totalLabel = receiptStr(R.string.receipt_label_total) + "(" + currency + "):";
                mUsbThermalPrinter.addString(totalLabel + padLeft(grandTotal, TSIZE26 - totalLabel.length()));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);

                // â”€â”€ TAX SUMMARY LOOP â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                List<SaleReceiptRes.TaxSummery> taxSummaryList = data.getTax_summery();
                if (taxSummaryList != null && !taxSummaryList.isEmpty()) {

                    for (SaleReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                        if (taxSummary.getCode() != null) {
                            String taxLabel = receiptStr(R.string.receipt_label_total_prefix) + taxSummary.getCode_name();
                            double taxableValue = (taxSummary.getTaxable_value() != null)
                                    ? taxSummary.getTaxable_value() : 0.0;
                            mUsbThermalPrinter.addString(taxLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxableValue)),
                                    TSIZE22 - taxLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    for (SaleReceiptRes.TaxSummery taxSummary : taxSummaryList) {
                        Double taxAmt = taxSummary.getTax_amount();
                        String code = taxSummary.getCode();
                        if (taxAmt != null && taxAmt != 0.0 && code != null) {
                            String taxAmountLabel = receiptStr(R.string.receipt_label_total_tax_prefix) + code;
                            mUsbThermalPrinter.addString(taxAmountLabel + padLeft(
                                    FunUtils.INSTANCE.formatPrintPrice(String.valueOf(taxAmt)),
                                    TSIZE22 - taxAmountLabel.length()
                            ));
                            mUsbThermalPrinter.printString();
                        }
                    }

                    String totalTaxAmount = data.getTax_amount();
                    if (totalTaxAmount != null && !totalTaxAmount.equals("0")
                            && !totalTaxAmount.equals("0.00")) {
                        String totalTaxLabel = receiptStr(R.string.receipt_label_total_tax_amount);
                        mUsbThermalPrinter.addString(totalTaxLabel + padLeft(
                                FunUtils.INSTANCE.formatPrintPrice(totalTaxAmount),
                                TSIZE22 - totalTaxLabel.length()
                        ));
                        mUsbThermalPrinter.printString();
                    }
                }

                mUsbThermalPrinter.walkPaper(1);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // Items count
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                int itemsCount = (data.getSalesItem() != null) ? data.getSalesItem().size() : 0;
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_items) + padLeft(Integer.toString(itemsCount), TSIZE22 - 6));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                String rcptTypeRaw = receiptStr(R.string.receipt_type_copy).toUpperCase(Locale.getDefault());
                mUsbThermalPrinter.setTextSize(24);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.addString(rcptTypeRaw);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ SDC INFORMATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setBold(true);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_sdc_info));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);

                if (data.getVsdc_reciept() != null && !data.getVsdc_reciept().isEmpty()) {
                    SaleReceiptRes.VsdcReceipt vsdc = data.getVsdc_reciept().get(0);

                    // â”€â”€ CHANGED 2: Date parsed properly with yyyyMMddHHmmss â”€â”€
                    String vsdcRawDate = (vsdc.getVsdcRcptPbctDate() != null)
                            ? vsdc.getVsdcRcptPbctDate() : "";
                    String vsdcDate = "";
                    String vsdcTime = "";
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
                        Date parsedVsdcDate = inputFormat.parse(vsdcRawDate);
                        if (parsedVsdcDate != null) {
                            vsdcDate = formatReceiptDate(parsedVsdcDate);
                            vsdcTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedVsdcDate);
                        }
                    } catch (ParseException e) {
                        Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                        vsdcDate = vsdcRawDate; // fallback
                    }

                    String vsdcDateLabel = receiptStr(R.string.receipt_label_date) + " " + vsdcDate;
                    String vsdcTimeLabel = receiptStr(R.string.receipt_label_time) + " " + vsdcTime;
                    int vsdcSpaces = TSIZE22 - vsdcDateLabel.length() - vsdcTimeLabel.length();
                    if (vsdcSpaces < 1) vsdcSpaces = 1;
                    StringBuilder vsdcDateLine = new StringBuilder(vsdcDateLabel);
                    for (int i = 0; i < vsdcSpaces; i++) vsdcDateLine.append(" ");
                    vsdcDateLine.append(vsdcTimeLabel);

                    mUsbThermalPrinter.addString(vsdcDateLine.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: SDC ID with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String sdcId = (vsdc.getSdcId() != null) ? vsdc.getSdcId() : "";
                    String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                    mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: Receipt Number (SDC) with padLeft â”€â”€â”€â”€â”€
                    String rcptNo = (vsdc.getRcptNo() != null && vsdc.getRcptNo() != 0)
                            ? String.valueOf(vsdc.getRcptNo()) : "";
                    String sdcRcptLabel = receiptStr(R.string.receipt_label_receipt_no);
                    String sdcRcptValue = rcptNo + "/" + vsdc.getTotRcptNo() + " CS";
                    mUsbThermalPrinter.addString(sdcRcptLabel + padLeft(sdcRcptValue, TSIZE22 - sdcRcptLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                    String intrlData = (vsdc.getIntrlData() != null) ? vsdc.getIntrlData() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + intrlData);
                    mUsbThermalPrinter.printString();

                    String rcptSign = (vsdc.getRcptSign() != null) ? vsdc.getRcptSign() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + rcptSign);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ RECEIPT NUMBER / DATE / MRC NO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                    mUsbThermalPrinter.addString("----------------------------------");
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);

                    // â”€â”€ CHANGED 1: Receipt Number with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String receiptNum = (data.getInvoice_id() != null) ? data.getInvoice_id() : "";
                    String rcptNumLabel = receiptStr(R.string.receipt_label_receipt_no);
                    mUsbThermalPrinter.addString(rcptNumLabel + padLeft(receiptNum, TSIZE22 - rcptNumLabel.length()));
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 2: Purchase date properly parsed â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String rawPurchaseDateTime = (data.getPurchase_date_time() != null)
                            ? data.getPurchase_date_time() : "";
                    String purchaseDate = "";
                    String purchaseTime = "";
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                        Date parsedDate = inputFormat.parse(rawPurchaseDateTime);
                        if (parsedDate != null) {
                            purchaseDate = formatReceiptDate(parsedDate);
                            purchaseTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(parsedDate);
                        }
                    } catch (ParseException e) {
                        Log.e("DateFormat", "Parsing failed: " + e.getMessage());
                        purchaseDate = rawPurchaseDateTime; // fallback
                    }

                    String purchaseDateLabel = receiptStr(R.string.receipt_label_date) + " " + purchaseDate;
                    String purchaseTimeLabel = receiptStr(R.string.receipt_label_time) + " " + purchaseTime;
                    int purchaseSpaces = TSIZE22 - purchaseDateLabel.length() - purchaseTimeLabel.length();
                    if (purchaseSpaces < 1) purchaseSpaces = 1;
                    StringBuilder purchaseDateLine = new StringBuilder(purchaseDateLabel);
                    for (int i = 0; i < purchaseSpaces; i++) purchaseDateLine.append(" ");
                    purchaseDateLine.append(purchaseTimeLabel);

                    mUsbThermalPrinter.addString(purchaseDateLine.toString());
                    mUsbThermalPrinter.printString();

                    // â”€â”€ CHANGED 1: MRC No with padLeft â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    String mrcLabel = receiptStr(R.string.receipt_label_mrc_no);
                    String mrcValue = (vsdc.getMrcNo() != null) ? vsdc.getMrcNo() + "." : ".";
                    mUsbThermalPrinter.addString(mrcLabel + padLeft(mrcValue, TSIZE22 - mrcLabel.length()));
                    mUsbThermalPrinter.printString();

                } else {

                    String sdcId = (data.getSdc_id() != null) ? data.getSdc_id() : "";
                    String sdcIdLabel = receiptStr(R.string.receipt_label_sdc_id);
                    mUsbThermalPrinter.addString(sdcIdLabel + padLeft(sdcId, TSIZE22 - sdcIdLabel.length()));
                    mUsbThermalPrinter.printString();

                    String receiptNo = (data.getReceipt_no() != null) ? data.getReceipt_no() : "";
                    String fallbackRcptLabel = context.getString(R.string.receipt_label_receipt_no);
                    mUsbThermalPrinter.addString(fallbackRcptLabel + padLeft(receiptNo + " CS", TSIZE22 - fallbackRcptLabel.length()));
                    mUsbThermalPrinter.printString();

                    mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);

                    String internalData = (data.getInternal_data() != null) ? data.getInternal_data() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_internal_data) + "  " + internalData);
                    mUsbThermalPrinter.printString();

                    String receiptSign = (data.getReceipt_sign() != null) ? data.getReceipt_sign() : "";
                    mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_signature) + "  " + receiptSign);
                    mUsbThermalPrinter.printString();
                    mUsbThermalPrinter.walkPaper(1);
                }

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.addString("----------------------------------");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);

                // â”€â”€ FOOTER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(22);
                mUsbThermalPrinter.setBold(false);
                mUsbThermalPrinter.setGray(6);
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_thank_you));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_appreciate_biz));
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(3);

                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                mUsbThermalPrinter.setTextSize(20);
                mUsbThermalPrinter.addString("*** END ***");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.addString(" ");
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(5);
                mUsbThermalPrinter.reset();

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("PrinterUtil", "Print error: " + e.getMessage(), e);
                Result = e.toString();
                if (Result.contains("NoPaperException")) {
                    nopaper = true;
                } else if (Result.contains("OverHeatException")) {
                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                } else {
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }
            } finally {
                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                if (nopaper) {
                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                    nopaper = false;
                    return;
                }
            }

        } else if (receiptType.equalsIgnoreCase("moz")) {


        } else if (receiptType.equalsIgnoreCase("type6")) {

        }
    }










//    private class contentPrintThread extends Thread {
//        public void run() {
//            super.run();
//            try {
//
////                val top: String,
////                        val storeName: String,
////                        val storeAddress: String,
////                        val vatInfo: String,
////                        val dateTime: String,
////                        val receiptInfo: String,
////                        val itemInfo:String,
////                        val totalPrice: String,
////                        val itemCount: String,
////                        val taxInfo: String,
////                        val totalVat: String,
////                        val payableAmount: String,
////                        val paymentModeInfo: String,
////                        val ejInfo: String,
////                        val bottom: String
//
//
//                ReceiptData receipt = receipt_data;
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////                mUsbThermalPrinter.setLeftIndent(1);
////                mUsbThermalPrinter.setLineSpace(3);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(3);
////                mUsbThermalPrinter.setBold(false);
////               // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getTop());
////                mUsbThermalPrinter.printString();
////               mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////                mUsbThermalPrinter.setTextSize(22);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(true);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getStoreName());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getStoreAddress());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getVatInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getDateTime());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getReceiptInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.walkPaper(1);
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getItemInfo());
////                mUsbThermalPrinter.setMonoSpace(true);
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(22);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(true);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getTotalPrice());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(2);
////
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getItemCount());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getTaxInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(22);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(true);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getTotalVat());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(22);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(true);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getPayableAmount());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getPaymentModeInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
//
//
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getEjInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
//
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getBottom());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
//
//
//                mUsbThermalPrinter.reset();
//                //mUsbThermalPrinter.set(1);
//
//                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
//               // mUsbThermalPrinter.setTextSize(20);
//                mUsbThermalPrinter.setGray(6);
//                mUsbThermalPrinter.setBold(false);
//
//                // mUsbThermalPrinter.setItalic(true);
//
////                for  ( SalesItem item : receipt.getProduct_item()  ){
////
////                    String[] colsTestArr = {item.getProduct_name()+"-"+item.getDistribution_pack().getProduct_description()
////                            , item.getQuantity() +" X "+item.getRetail_price(), item.getTotal_amount()+"0000"};
////                    int[] colsWidthArr = {100, 50, 50};
////                    int[] colsAlign = {0, 1, 2}; // Example alignment values
////                    int colsTextSize = 20;
////
////                     mUsbThermalPrinter.addColumnsString(colsTestArr, colsWidthArr, colsAlign, colsTextSize);
////
////                     mUsbThermalPrinter.printString();
////                     mUsbThermalPrinter.walkPaper(1);
////
////
////                }
//
//
////                   mUsbThermalPrinter.printColumnsString(new String[]{"Item 1","$2000.00",}, new int[]{6,2}, new int[]{0,0}, 20);
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setBold(true);
////                mUsbThermalPrinter.setGray(6);
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////
////                mUsbThermalPrinter.printColumnsString(new String[]{"Item 1123456","$200023.00",}, new int[]{6,3}, new int[]{0,0}, 22);
//
//                mUsbThermalPrinter.reset();
//                mUsbThermalPrinter.setBold(true);
//                mUsbThermalPrinter.setGray(6);
//                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
//
//                mUsbThermalPrinter.setUnderline(false);
//                mUsbThermalPrinter.setAlgin(1);
//                mUsbThermalPrinter.printStringAndWalk(0,0,2);
//
//
//
//                mUsbThermalPrinter.printColumnsString(new String[]{"COLUMN Aaaaaaa","Culoaaa B","20000aaaa"}, new int[]{6,3,3}, new int[]{0,1,2}, 22);
//
//
//
//                mUsbThermalPrinter.walkPaper(1);
//
//                mUsbThermalPrinter.autoBreakSet(true);
//                mUsbThermalPrinter.addString("----------------------------");
//                mUsbThermalPrinter.printString();
//
//
    ////                String[] colsTestArr = {"Column1", "Column2", "Column3"};
    //////                int[] colsWidthArr = {100, 200, 150};
    //////                int[] colsAlign = {1, 2, 3}; // Example alignment values
    //////                int colsTextSize = 12;
//
//                // Calling the method
//               // mUsbThermalPrinter.addColumnsString(colsTestArr, colsWidthArr, colsAlign, colsTextSize);
//               // mUsbThermalPrinter.printColumnsString(colsTestArr, colsWidthArr, colsAlign, colsTextSize);
//              //  mUsbThermalPrinter.addColumnsString(new String[]{"AAA","BBB","CCC"}, new int[]{6,6,6}, new int[]{0,0,0}, 16);
//
//               // mUsbThermalPrinter.printString();
//                mUsbThermalPrinter.walkPaper(1);
//
//
//
//                mUsbThermalPrinter.walkPaper(1);
//                mUsbThermalPrinter.reset();
//
//
//                Toast.makeText(context, "Printing initiated", Toast.LENGTH_LONG).show();
//
//            } catch (Exception e) {
//                e.printStackTrace();
//                System.out.println(e.toString());
//
//                Result = e.toString();
//                if (Result.contains("NoPaperException")) {
//                    nopaper = true;
//                } else if (Result.contains("OverHeatException")) {
//                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
//                } else {
//                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
//                }
//            }finally {
//                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
//                if (nopaper) {
//                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
//                    nopaper = false;
//                    return;
//                }
//            }
//        }
//    }

    private String generateReceipt(ArrayList<MyItem> items) {
        StringBuilder printContent = new StringBuilder();
        printContent.append("\n             RetailOne\n")
                .append("---------------------------\n")
                .append("DateÃ¯Â¼Å¡2015-01-01 16:18:20\n")
                .append("invoiceÃ¯Â¼Å¡12378945664\n")
                .append("idÃ¯Â¼Å¡1001000000000529142\n")
                .append("---------------------------\n")
                .append("    item        quantity   Price  total\n");

        double total = 0;
        for (SalesItem item : posSalesDetails.getData().getSalesItem()) {
            // double itemTotal = item.getQuantity() * item.getPrice();
            printContent.append(String.format("%-14s %8d %10.2f %10.2f\n", formatItemName(item.getProduct_name()), item.getQuantity(), item.getTax_inclusive_price(), item.getTotal_amount()));
            //total += itemTotal;
        }

        printContent.append("----------------------------\n")
                .append(String.format(" taxÃ¯Â¼Å¡%10.2f\n", 1000.00))
                .append("----------------------------\n")
                .append(String.format("paidÃ¯Â¼Å¡%10.2f\n", 10000.00))
                .append(String.format("tenderÃ¯Â¼Å¡%10.2f\n", 1000.00))
                .append(String.format("paidÃ¯Â¼Å¡%10.2f\n", 9000.00))
                .append("----------------------------\n")
                .append(" Thanks for shopping with us\n")
                .append("tel :1111111111\n");

        return printContent.toString();
    }

    private static String formatItemName(String name) {
        if (name.length() > 14) {
            return name.substring(0, 14);
        } else {
            return String.format("%-14s", name);
        }
    }

    public void registerBatteryReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction("android.intent.action.BATTERY_CAPACITY_EVENT");
        context.registerReceiver(printReceive, filter);
    }

    public void unregisterBatteryReceiver() {
        context.unregisterReceiver(printReceive);
    }

    private final BroadcastReceiver printReceive = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_NOT_CHARGING);
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);

                if (deviceType == StringUtil.DeviceModelEnum.TPS390.ordinal()) {
                    LowBattery = level * 5 <= scale;
                } else if (SystemUtil.getInternalModel().equals("M8")) {
                    LowBattery = level * 10 <= scale;
                } else {
                    LowBattery = status != BatteryManager.BATTERY_STATUS_CHARGING && level * 5 <= scale;
                }
            } else if (action.equals("android.intent.action.BATTERY_CAPACITY_EVENT")) {
                int status = intent.getIntExtra("action", 0);
                int level = intent.getIntExtra("level", 0);
                LowBattery = status == 0 && level < 1;
            }
        }
    };
}

































/*
package com.retailone.pos.utils;


import static android.provider.MediaStore.Images.Media.getBitmap;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.Spanned;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.common.apiutil.CommonException;
import com.common.apiutil.printer.NewUsbThermalPrinter;
import com.common.apiutil.printer.UsbThermalPrinter;
//import com.common.apiutil.util.SDKUtil;
import com.common.apiutil.util.StringUtil;
import com.common.apiutil.util.SystemUtil;
import com.retailone.pos.R;
import com.retailone.pos.localstorage.SharedPreference.LocalizationHelper;
import com.retailone.pos.models.LocalizationModel.LocalizationData;
import com.retailone.pos.models.PosSalesDetailsModel.PosSalesDetails;
import com.retailone.pos.models.PosSalesDetailsModel.SalesItem;
import com.retailone.pos.models.PrinterModel.ReceiptData;
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnSaleRes;
import com.retailone.pos.models.ReturnSalesItemModel.ReturnSaleResModel.ReturnedItem;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;

public class PrinterUtil {

    private String printVersion;
    private final int NOPAPER = 3;
    private final int LOWBATTERY = 4;
    private final int PRINTVERSION = 5;
    private final int PRINTBARCODE = 6;
    private final int PRINTQRCODE = 7;
    private final int PRINTPAPERWALK = 8;
    private final int PRINTCONTENT = 9;
    private final int CANCELPROMPT = 10;
    private final int PRINTERR = 11;
    private final int OVERHEAT = 12;
    private final int MAKER = 13;
    private final int PRINTPICTURE = 14;
    private final int NOBLACKBLOCK = 15;
    private final int PRINTSHORTCONTENT = 16;
    private final int PRINTLONGPICTURE = 17;
    private final int PRINTLONGTEXT = 18;
    private final int PRINTBLACK = 19;
    private final int PRINTCOLUMNS = 20;

    private NewUsbThermalPrinter mUsbThermalPrinter;
    private ProgressDialog dialog;
    private ProgressDialog progressDialog;
    private MyHandler handler;
    private boolean LowBattery = false;
    private Context context;
    private int deviceType;
    private boolean nopaper = false;
    ArrayList<MyItem> itemx ;

    private String Result;

    private String currency;
    private String zone;
    String printType = "";
    LocalizationData localizationData;




    PosSalesDetails posSalesDetails;
    ReturnSaleRes returnSaleRes;
    ReceiptData receipt_data;

    public PrinterUtil(Context context) {
        this.context = context;
        mUsbThermalPrinter = new NewUsbThermalPrinter(context);
        deviceType = SystemUtil.getDeviceType();
        handler = new MyHandler();
        currency =  new LocalizationHelper(context).getLocalizationData().getCurrency();
        zone =  new LocalizationHelper(context).getLocalizationData().getTimezone();

       /// SDKUtil.getInstance(context).initSDK();
        initializePrinter();
    }

    private void initializePrinter() {
        dialog = new ProgressDialog(context);
        dialog.setTitle(context.getString(R.string.printer_initializing_title));
        dialog.setMessage(context.getString(R.string.please_wait));
        dialog.setCancelable(false);
        dialog.show();

        new Thread(() -> {
            try {
                mUsbThermalPrinter.start(0);
                mUsbThermalPrinter.reset();
            } catch (CommonException e) {
                e.printStackTrace();
            } finally {
                dialog.dismiss();
            }
        }).start();
    }

    public void printReceiptData(PosSalesDetails _posSalesDetails) {
        printType = "SALE";

      //  receipt_data = receiptData;
        posSalesDetails = _posSalesDetails;

        if (LowBattery) {
            handler.sendMessage(handler.obtainMessage(LOWBATTERY, 1, 0, null));
        } else {
            if (!nopaper) {
               // handler.sendMessage(handler.obtainMessage(PRINTPICTURE, 1, 0, null));

                handler.sendMessage(handler.obtainMessage(PRINTCONTENT, 1, 0, null));
                //Toast.makeText(context, "Paper Available", Toast.LENGTH_LONG).show();

            } else {
                Toast.makeText(context, context.getString(R.string.toast_no_paper_detected), Toast.LENGTH_LONG).show();
            }
        }

    }

    public void printReturnReceiptData( ReturnSaleRes _returnSaleRes) {
        printType = "RETURN";

        returnSaleRes = _returnSaleRes;

        if (LowBattery) {
            handler.sendMessage(handler.obtainMessage(LOWBATTERY, 1, 0, null));
        } else {
            if (!nopaper) {
               // handler.sendMessage(handler.obtainMessage(PRINTPICTURE, 1, 0, null));

                handler.sendMessage(handler.obtainMessage(PRINTCONTENT, 1, 0, null));
                //Toast.makeText(context, "Paper Available", Toast.LENGTH_LONG).show();

            } else {
                Toast.makeText(context, context.getString(R.string.toast_no_paper_detected), Toast.LENGTH_LONG).show();
            }
        }

    }

//    public void printReceipt(@NotNull PosSalesDetails posSaleData) {
//
//        posSalesDetails = posSaleData;
//
//        if (LowBattery) {
//            handler.sendMessage(handler.obtainMessage(4, 1, 0, null));
//        } else {
//            if (!nopaper) {
//                handler.sendMessage(handler.obtainMessage(9, 1, 0, null));
//                Toast.makeText(context, "Paper Available", Toast.LENGTH_LONG).show();
//
//            } else {
//                Toast.makeText(context, context.getString(R.string.toast_no_paper_detected), Toast.LENGTH_LONG).show();
//            }
//        }
//
//    }

    private class MyHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NOPAPER:
                    //NOPAPER
                    noPaperDlg();
                    break;
                case LOWBATTERY:
                    //LOWBATTERY
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                    alertDialog.setTitle(context.getString(R.string.operation_result));
                    alertDialog.setMessage(context.getString(R.string.LowBattery));
                    alertDialog.setPositiveButton("OK", (dialog, which) -> {
                    });
                    alertDialog.show();
                    break;
                case PRINTCONTENT:
                    //Toast.makeText(context, "printContent", Toast.LENGTH_LONG).show();
                    new contentPrintThread().start();
                    break;

                case PRINTPICTURE:
                    new printPicture().start();
                    break;

                case NOBLACKBLOCK:
                    //NOBLACKBLOCK
                    Toast.makeText(context, R.string.maker_not_find, Toast.LENGTH_SHORT).show();
                    break;
//                case 10:
//                    //CANCELPROMPT
//                    if (progressDialog != null && !UsbPrinterActivity.this.isFinishing()) {
//                        progressDialog.dismiss();
//                        progressDialog = null;
//                    }
//                    break;
                default:
                   // Toast.makeText(context, "Print Error!", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    private void noPaperDlg() {
        AlertDialog.Builder dlg = new AlertDialog.Builder(context);
        dlg.setTitle(context.getString(R.string.noPaper));
        dlg.setMessage(context.getString(R.string.noPaperNotice));
        dlg.setCancelable(false);
        dlg.setPositiveButton("OK", (dialog, which) -> {
        });
        dlg.show();
    }



    private class printPicture extends Thread {

        public void run() {
            super.run();
            try {
                mUsbThermalPrinter.reset();
                mUsbThermalPrinter.setGray(3);
                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
                //File file = new File(picturePath);
                //if (file.exists()) {
                mUsbThermalPrinter.printLogo(drawableToBitmap(ContextCompat.getDrawable(context,R.drawable.mlogo)), false);
                mUsbThermalPrinter.walkPaper(20);
				*/
/*} else {
					runOnUiThread(new Runnable() {


						public void run() {
							Toast.makeText(UsbPrinterActivity.this, getString(R.string.not_find_picture),
									Toast.LENGTH_LONG).show();
						}
					});
				}*//*

            } catch (Exception e) {
                e.printStackTrace();
                Result = e.toString();
                if (Result.contains("NoPaperException")) {
                    nopaper = true;
                } else if (Result.contains("OverHeatException")) {
                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
                } else {
                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
                }
            } finally {
                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
                if (nopaper) {
                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                    nopaper = false;
                    return;
                }
            }
        }
    }


    public Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            // If the drawable is a BitmapDrawable, just return its bitmap
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            // Otherwise, create a new bitmap and draw the drawable on it
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();

            // Ensure dimensions are valid, otherwise, use default dimensions
            width = width > 0 ? width : 1;
            height = height > 0 ? height : 1;

            // Create a bitmap with the specified width and height
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            // Create a canvas to draw on the bitmap
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }

        return bitmap;
    }


    private class contentPrintThread extends Thread {
        public void run() {
            super.run();


                if(printType.equals("SALE")){

                    printSaleType(posSalesDetails);

                }else if(printType.equals("RETURN")){

                    printReturnType(returnSaleRes);


            }
        }
    }

    private void printReturnType(ReturnSaleRes details) {
        com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper orgHelper = new com.retailone.pos.localstorage.SharedPreference.OrganisationDetailsHelper(context);
        String receiptType = orgHelper.getOrganisationData().getReciept_type();
        if (receiptType == null || receiptType.trim().isEmpty()) {
            receiptType = "rra";
        }

        if (receiptType.equalsIgnoreCase("rra")) {
            try {

                mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setLeftIndent(1);
            mUsbThermalPrinter.setLineSpace(3);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(3);
            mUsbThermalPrinter.setBold(false);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.addString("*** START OF LEGEAL RECEIPT ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(3);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(true);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(2);

            mUsbThermalPrinter.addString(receiptStr(R.string.receipt_label_credit_note));
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.printColumnsString(new String[]{receiptStr(R.string.receipt_label_vat_no),details.getData().getVat_no(),}, new int[]{3,6}, new int[]{0,1}, 22);
            mUsbThermalPrinter.printColumnsString(new String[]{receiptStr(R.string.receipt_label_tpin_no),details.getData().getTpin_no(),}, new int[]{3,6}, new int[]{0,1}, 22);

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.printColumnsString(new String[]{"DATEÃ¯Â¼Å¡",DateTimeFormatting.Companion.formatSaleReturndate(details.getData().getReturned_date(), zone, context),}, new int[]{3,6}, new int[]{0,1}, 22);

            mUsbThermalPrinter.walkPaper(2);
            mUsbThermalPrinter.printColumnsString(new String[]{"Credit Note NoÃ¯Â¼Å¡",details.getData().getReturned_invoice_id(),}, new int[]{5,5}, new int[]{0,1}, 22);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_buyer_name),details.getData().getCustomer_name(),}, new int[]{5,5}, new int[]{0,1}, 22);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_buyer_tin),details.getData().getBuyers_tpin(),}, new int[]{5,5}, new int[]{0,1}, 22);
            mUsbThermalPrinter.walkPaper(2);


            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            //mUsbThermalPrinter.set(1);

            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            // mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            for (ReturnedItem item : details.getData().getReturned_items()){
                String[] colsTestArr = {item.getProduct_name()+"("+item.getDistribution_pack_name()+")"
                        , " "+FunUtils.INSTANCE.DtoString(item.getQuantity()) +" X "+FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getRetail_price()))," "+ FunUtils.INSTANCE.formatPrintPrice(Double.toString(item.getTotal_amount()))};
                int[] colsWidthArr = {5, 4, 3};
                int[] colsAlign = {0, 1, 2}; // Example alignment values
                int colsTextSize = 20;

                mUsbThermalPrinter.addColumnsString(colsTestArr, colsWidthArr, colsAlign, colsTextSize);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            }


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            //mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.walkPaper(1);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(true);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.printColumnsString(new String[]{"TOTAL ("+currency+") Ã¯Â¼Å¡",FunUtils.INSTANCE.formatPrintPrice(Double.toString(details.getData().getTotal())),}, new int[]{6,3}, new int[]{0,1}, 22);

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            //mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.printColumnsString(new String[]{"ItemsÃ¯Â¼Å¡",Integer.toString(details.getData().getReturned_items().size())}, new int[]{6,3}, new int[]{0,1}, 22);


            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.printColumnsString(new String[]{"TAX EXÃ¯Â¼Å¡",FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_ex())}, new int[]{6,3}, new int[]{0,1}, 22);
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.printColumnsString(new String[]{"TAX VAT@"+details.getData().getTax()+"% ",FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_amount())}, new int[]{6,3}, new int[]{0,1}, 22);
            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(true);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.printColumnsString(new String[]{"TOTAL VATÃ¯Â¼Å¡",FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_amount()),}, new int[]{6,3}, new int[]{0,1}, 22);

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            //mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(true);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.printColumnsString(new String[]{"PAYABLEÃ¯Â¼Å¡",FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()),}, new int[]{6,3}, new int[]{0,1}, 22);

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            //mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);


            mUsbThermalPrinter.printColumnsString(new String[]{"EJ NO:",details.getData().getEj_no(),}, new int[]{5,5}, new int[]{0,1}, 22);
            mUsbThermalPrinter.printColumnsString(new String[]{"EJ ACTIVATION DATE:",details.getData().getEj_activation_date(),}, new int[]{5,5}, new int[]{0,1}, 22);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_sdc_id),details.getData().getSdc_id(),}, new int[]{5,5}, new int[]{0,1}, 22);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_receipt_no_short),details.getData().getReceipt_no(),}, new int[]{5,5}, new int[]{0,1}, 22);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_internal_data),details.getData().getInternal_data(),}, new int[]{5,5}, new int[]{0,1}, 22);
            mUsbThermalPrinter.walkPaper(2);

            mUsbThermalPrinter.printColumnsString(new String[]{"Receipt Sign:",details.getData().getReceipt_sign(),}, new int[]{5,5}, new int[]{0,1}, 22);


            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(2);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setLeftIndent(1);
            mUsbThermalPrinter.setLineSpace(3);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(3);
            mUsbThermalPrinter.setBold(false);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.addString("*** END OF LEGEAL RECEIPT ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(15);

            mUsbThermalPrinter.reset();


            // Toast.makeText(context, "Printing initiated", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.toString());

            Result = e.toString();
            if (Result.contains("NoPaperException")) {
                nopaper = true;
            } else if (Result.contains("OverHeatException")) {
                handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
            } else {
                handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
            }
        }finally {
            handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
            if (nopaper) {
                handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                nopaper = false;
                return;
            }
        }
        } else if (receiptType.equalsIgnoreCase("type2")) {

        } else if (receiptType.equalsIgnoreCase("type3")) {

        } else if (receiptType.equalsIgnoreCase("type4")) {

        } else if (receiptType.equalsIgnoreCase("type5")) {

        } else if (receiptType.equalsIgnoreCase("type6")) {

        }
    }

    private void printSaleType(PosSalesDetails details) {

        try {

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setLeftIndent(1);
            mUsbThermalPrinter.setLineSpace(3);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(3);
            mUsbThermalPrinter.setBold(false);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.addString("*** START OF LEGEAL RECEIPT ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(3);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(22);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(true);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.addString(details.getData().getStore().getStore_name().toString().toUpperCase());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.addString(details.getData().getStore().getAddress().toString().toUpperCase());
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(2);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.printColumnsString(new String[]{receiptStr(R.string.receipt_label_vat_no),details.getData().getVat_no(),}, new int[]{3,6}, new int[]{0,1}, 20);
            mUsbThermalPrinter.printColumnsString(new String[]{receiptStr(R.string.receipt_label_tpin_no),details.getData().getTpin_no(),}, new int[]{3,6}, new int[]{0,1}, 20);

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.printColumnsString(new String[]{"DATEÃ¯Â¼Å¡",DateTimeFormatting.Companion.formatSaleReturndate(details.getData().getPurchase_date_time(), zone, context),}, new int[]{3,6}, new int[]{0,1}, 22);

            mUsbThermalPrinter.walkPaper(2);
            mUsbThermalPrinter.printColumnsString(new String[]{"RECEIPT NOÃ¯Â¼Å¡",details.getData().getInvoice_id(),}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_buyer_name),details.getData().getCustomer_name(),}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_buyer_tin),details.getData().getBuyers_tpin(),}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.walkPaper(2);


            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            //mUsbThermalPrinter.set(1);

            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            // mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            for (SalesItem item : details.getData().getSalesItem()){
                String[] colsTestArr = {item.getProduct_name()+"("+item.getDistribution_pack().getProduct_description()+")"
                        , " "+FunUtils.INSTANCE.DtoString(item.getQuantity()) +" X "+ FunUtils.INSTANCE.formatPrintPrice(item.getRetail_price())," "+ FunUtils.INSTANCE.formatPrintPrice(item.getTotal_amount())};
                int[] colsWidthArr = {5, 4, 3};
                int[] colsAlign = {0, 1, 2}; // Example alignment values
                int colsTextSize = 18;

                mUsbThermalPrinter.addColumnsString(colsTestArr, colsWidthArr, colsAlign, colsTextSize);
                mUsbThermalPrinter.printString();
                mUsbThermalPrinter.walkPaper(1);
            }


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            //mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.walkPaper(1);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(true);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.printColumnsString(new String[]{"TOTAL ("+currency+") Ã¯Â¼Å¡",FunUtils.INSTANCE.formatPrintPrice(details.getData().getSub_total()),}, new int[]{5,5}, new int[]{0,1}, 20);

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            //mUsbThermalPrinter.walkPaper(1);
            mUsbThermalPrinter.printColumnsString(new String[]{"ItemsÃ¯Â¼Å¡",Integer.toString(details.getData().getSalesItem().size())}, new int[]{5,5}, new int[]{0,1}, 20);


            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.printColumnsString(new String[]{"TAX EXÃ¯Â¼Å¡",FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_ex())}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.printColumnsString(new String[]{"TAX VAT@"+details.getData().getTax()+"% ",FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_amount())}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(true);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.printColumnsString(new String[]{"TOTAL VATÃ¯Â¼Å¡",FunUtils.INSTANCE.formatPrintPrice(details.getData().getTax_amount()),}, new int[]{5,5}, new int[]{0,1}, 20);

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            //mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(true);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.printColumnsString(new String[]{"PAYABLEÃ¯Â¼Å¡",FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()),}, new int[]{5,5}, new int[]{0,1}, 20);

            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(4);
            mUsbThermalPrinter.setBold(false);
            //mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(1);

            mUsbThermalPrinter.printColumnsString(new String[]{"CASHÃ¯Â¼Å¡",details.getData().getPayment_type().trim().toUpperCase().equals("CASH") ?
                    FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()) : "XXX"}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.printColumnsString(new String[]{receiptStr(R.string.receipt_label_card_mmoney),
                    details.getData().getPayment_type().trim().toUpperCase().equals("CARD") ||
                            details.getData().getPayment_type().trim().toUpperCase().equals("M-MONEY") ?
                            FunUtils.INSTANCE.formatPrintPrice(details.getData().getGrand_total()) : "xxx"}, new int[]{5,5}, new int[]{0,1}, 20);


            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();

            mUsbThermalPrinter.printColumnsString(new String[]{"EJ NO:",details.getData().getEj_no(),}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.printColumnsString(new String[]{"EJ ACTIVATION DATE:",details.getData().getEj_activation_date(),}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_sdc_id),details.getData().getTax_sdc_idamount(),}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_receipt_no_short),details.getData().getReceipt_no(),}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.printColumnsString(new String[]{context.getString(R.string.receipt_label_internal_data),details.getData().getInternal_data(),}, new int[]{5,5}, new int[]{0,1}, 20);
            mUsbThermalPrinter.walkPaper(2);

            mUsbThermalPrinter.printColumnsString(new String[]{"Receipt Sign:",details.getData().getReceipt_sign(),}, new int[]{5,5}, new int[]{0,1}, 20);


            mUsbThermalPrinter.addString("----------------------------");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(2);


            mUsbThermalPrinter.reset();
            mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
            mUsbThermalPrinter.setLeftIndent(1);
            mUsbThermalPrinter.setLineSpace(3);
            mUsbThermalPrinter.setTextSize(20);
            mUsbThermalPrinter.setGray(3);
            mUsbThermalPrinter.setBold(false);
            // mUsbThermalPrinter.setItalic(true);
            mUsbThermalPrinter.addString("*** END OF LEGEAL RECEIPT ***");
            mUsbThermalPrinter.printString();
            mUsbThermalPrinter.walkPaper(15);

            mUsbThermalPrinter.reset();


            // Toast.makeText(context, "Printing initiated", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.toString());

            Result = e.toString();
            if (Result.contains("NoPaperException")) {
                nopaper = true;
            } else if (Result.contains("OverHeatException")) {
                handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
            } else {
                handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
            }
        }finally {
            handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
            if (nopaper) {
                handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
                nopaper = false;
                return;
            }
        }

    }


//    private class contentPrintThread extends Thread {
//        public void run() {
//            super.run();
//            try {
//
////                val top: String,
////                        val storeName: String,
////                        val storeAddress: String,
////                        val vatInfo: String,
////                        val dateTime: String,
////                        val receiptInfo: String,
////                        val itemInfo:String,
////                        val totalPrice: String,
////                        val itemCount: String,
////                        val taxInfo: String,
////                        val totalVat: String,
////                        val payableAmount: String,
////                        val paymentModeInfo: String,
////                        val ejInfo: String,
////                        val bottom: String
//
//
//                ReceiptData receipt = receipt_data;
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////                mUsbThermalPrinter.setLeftIndent(1);
////                mUsbThermalPrinter.setLineSpace(3);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(3);
////                mUsbThermalPrinter.setBold(false);
////               // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getTop());
////                mUsbThermalPrinter.printString();
////               mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////                mUsbThermalPrinter.setTextSize(22);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(true);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getStoreName());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getStoreAddress());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getVatInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getDateTime());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getReceiptInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.walkPaper(1);
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getItemInfo());
////                mUsbThermalPrinter.setMonoSpace(true);
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(22);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(true);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getTotalPrice());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(2);
////
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getItemCount());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getTaxInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(22);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(true);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getTotalVat());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(22);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(true);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getPayableAmount());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
////
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getPaymentModeInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
//
//
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getEjInfo());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
//
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////                mUsbThermalPrinter.setTextSize(20);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setBold(false);
////                // mUsbThermalPrinter.setItalic(true);
////                mUsbThermalPrinter.addString(receipt.getBottom());
////                mUsbThermalPrinter.printString();
////                mUsbThermalPrinter.walkPaper(1);
//
//
//                mUsbThermalPrinter.reset();
//                //mUsbThermalPrinter.set(1);
//
//                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
//               // mUsbThermalPrinter.setTextSize(20);
//                mUsbThermalPrinter.setGray(4);
//                mUsbThermalPrinter.setBold(false);
//
//                // mUsbThermalPrinter.setItalic(true);
//
////                for  ( SalesItem item : receipt.getProduct_item()  ){
////
////                    String[] colsTestArr = {item.getProduct_name()+"-"+item.getDistribution_pack().getProduct_description()
////                            , item.getQuantity() +" X "+item.getRetail_price(), item.getTotal_amount()+"0000"};
////                    int[] colsWidthArr = {100, 50, 50};
////                    int[] colsAlign = {0, 1, 2}; // Example alignment values
////                    int colsTextSize = 20;
////
////                     mUsbThermalPrinter.addColumnsString(colsTestArr, colsWidthArr, colsAlign, colsTextSize);
////
////                     mUsbThermalPrinter.printString();
////                     mUsbThermalPrinter.walkPaper(1);
////
////
////                }
//
//
////                   mUsbThermalPrinter.printColumnsString(new String[]{"Item 1","$2000.00",}, new int[]{6,2}, new int[]{0,0}, 20);
////                mUsbThermalPrinter.reset();
////                mUsbThermalPrinter.setBold(true);
////                mUsbThermalPrinter.setGray(4);
////                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_MIDDLE);
////
////                mUsbThermalPrinter.printColumnsString(new String[]{"Item 1123456","$200023.00",}, new int[]{6,3}, new int[]{0,0}, 22);
//
//                mUsbThermalPrinter.reset();
//                mUsbThermalPrinter.setBold(true);
//                mUsbThermalPrinter.setGray(4);
//                mUsbThermalPrinter.setAlgin(UsbThermalPrinter.ALGIN_LEFT);
//
//                mUsbThermalPrinter.setUnderline(false);
//                mUsbThermalPrinter.setAlgin(1);
//                mUsbThermalPrinter.printStringAndWalk(0,0,2);
//
//
//
//                mUsbThermalPrinter.printColumnsString(new String[]{"COLUMN Aaaaaaa","Culoaaa B","20000aaaa"}, new int[]{6,3,3}, new int[]{0,1,2}, 22);
//
//
//
//                mUsbThermalPrinter.walkPaper(1);
//
//                mUsbThermalPrinter.autoBreakSet(true);
//                mUsbThermalPrinter.addString("----------------------------");
//                mUsbThermalPrinter.printString();
//
//
////                String[] colsTestArr = {"Column1", "Column2", "Column3"};
//////                int[] colsWidthArr = {100, 200, 150};
//////                int[] colsAlign = {1, 2, 3}; // Example alignment values
//////                int colsTextSize = 12;
//
//                // Calling the method
//               // mUsbThermalPrinter.addColumnsString(colsTestArr, colsWidthArr, colsAlign, colsTextSize);
//               // mUsbThermalPrinter.printColumnsString(colsTestArr, colsWidthArr, colsAlign, colsTextSize);
//              //  mUsbThermalPrinter.addColumnsString(new String[]{"AAA","BBB","CCC"}, new int[]{6,6,6}, new int[]{0,0,0}, 16);
//
//               // mUsbThermalPrinter.printString();
//                mUsbThermalPrinter.walkPaper(1);
//
//
//
//                mUsbThermalPrinter.walkPaper(1);
//                mUsbThermalPrinter.reset();
//
//
//                Toast.makeText(context, "Printing initiated", Toast.LENGTH_LONG).show();
//
//            } catch (Exception e) {
//                e.printStackTrace();
//                System.out.println(e.toString());
//
//                Result = e.toString();
//                if (Result.contains("NoPaperException")) {
//                    nopaper = true;
//                } else if (Result.contains("OverHeatException")) {
//                    handler.sendMessage(handler.obtainMessage(OVERHEAT, 1, 0, null));
//                } else {
//                    handler.sendMessage(handler.obtainMessage(PRINTERR, 1, 0, null));
//                }
//            }finally {
//                handler.sendMessage(handler.obtainMessage(CANCELPROMPT, 1, 0, null));
//                if (nopaper) {
//                    handler.sendMessage(handler.obtainMessage(NOPAPER, 1, 0, null));
//                    nopaper = false;
//                    return;
//                }
//            }
//        }
//    }

    private String generateReceipt(ArrayList<MyItem> items) {
        StringBuilder printContent = new StringBuilder();
        printContent.append("\n             RetailOne\n")
                .append("---------------------------\n")
                .append("DateÃ¯Â¼Å¡2015-01-01 16:18:20\n")
                .append("invoiceÃ¯Â¼Å¡12378945664\n")
                .append("idÃ¯Â¼Å¡1001000000000529142\n")
                .append("---------------------------\n")
                .append("    item        quantity   Price  total\n");

        double total = 0;
        for (SalesItem item : posSalesDetails.getData().getSalesItem()) {
           // double itemTotal = item.getQuantity() * item.getPrice();
            printContent.append(String.format("%-14s %8d %10.2f %10.2f\n", formatItemName(item.getProduct_name()), item.getQuantity(), item.getRetail_price(), item.getTotal_amount()));
            //total += itemTotal;
        }

        printContent.append("----------------------------\n")
                .append(String.format(" taxÃ¯Â¼Å¡%10.2f\n", 1000.00))
                .append("----------------------------\n")
                .append(String.format("paidÃ¯Â¼Å¡%10.2f\n", 10000.00))
                .append(String.format("tenderÃ¯Â¼Å¡%10.2f\n", 1000.00))
                .append(String.format("paidÃ¯Â¼Å¡%10.2f\n", 9000.00))
                .append("----------------------------\n")
                .append(" Thanks for shopping with us\n")
                .append("tel :1111111111\n");

        return printContent.toString();
    }

    private static String formatItemName(String name) {
        if (name.length() > 14) {
            return name.substring(0, 14);
        } else {
            return String.format("%-14s", name);
        }
    }

    public void registerBatteryReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction("android.intent.action.BATTERY_CAPACITY_EVENT");
        context.registerReceiver(printReceive, filter);
    }

    public void unregisterBatteryReceiver() {
        context.unregisterReceiver(printReceive);
    }

    private final BroadcastReceiver printReceive = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_NOT_CHARGING);
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);

                if (deviceType == StringUtil.DeviceModelEnum.TPS390.ordinal()) {
                    LowBattery = level * 5 <= scale;
                } else if (SystemUtil.getInternalModel().equals("M8")) {
                    LowBattery = level * 10 <= scale;
                } else {
                    LowBattery = status != BatteryManager.BATTERY_STATUS_CHARGING && level * 5 <= scale;
                }
            } else if (action.equals("android.intent.action.BATTERY_CAPACITY_EVENT")) {
                int status = intent.getIntExtra("action", 0);
                int level = intent.getIntExtra("level", 0);
                LowBattery = status == 0 && level < 1;
            }
        }
    };
}

*/