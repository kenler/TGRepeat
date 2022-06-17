package com.dbfd.deng;


import android.content.Context;
import android.content.res.XModuleResources;
import android.text.SpannableString;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookInit implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
    private static final List<String> hookPackages = Arrays.asList("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta");
    private static String MODULE_PATH = null;
    private int mMsg_repeat = 0;
    private boolean isHookGIFADD = false;
    private Object popupWindow;
    private Object thisObj;
    private Class FileLoader;
    private Constructor photoEntryCt;
    private Field chatActivityEnterViewField;
    private Class AndroidUtilities;

    //    private Class aPop;
    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;//获取模块apk文件在储存中的位置
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        if (!hookPackages.contains(resparam.packageName))
            return;
        XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
        mMsg_repeat = resparam.res.addResource(modRes, R.drawable.msg_repeat);
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (hookPackages.contains(lpparam.packageName)) {
            FileLoader = lpparam.classLoader.loadClass("org.telegram.messenger.FileLoader");
            AndroidUtilities = lpparam.classLoader.loadClass("org.telegram.messenger.AndroidUtilities");

//            XposedBridge.log("------Hook成功 " + lpparam.packageName);
            /**
             * HOOK禁止群截图
             */
            XposedHelpers.findAndHookMethod("org.telegram.messenger.AndroidUtilities", lpparam.classLoader, "updateFlagSecure", Window.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Window window = (Window) param.args[0];
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            });

            /**
             * HOOK复制权限
             */
            XposedHelpers.findAndHookMethod("org.telegram.ui.Cells.ChatMessageCell", lpparam.classLoader, "getMessageObject", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Object ts = param.thisObject;
                    Object messageObjectToSet = XposedHelpers.getObjectField(ts, "messageObjectToSet");
                    Object currentMessageObject = XposedHelpers.getObjectField(ts, "currentMessageObject");
                    if (messageObjectToSet != null) {
                        XposedHelpers.setBooleanField(XposedHelpers.getObjectField(messageObjectToSet, "messageOwner"), "noforwards", false);
                        param.setResult(messageObjectToSet);
                    } else {
                        XposedHelpers.setBooleanField(XposedHelpers.getObjectField(currentMessageObject, "messageOwner"), "noforwards", false);
                        param.setResult(currentMessageObject);
                    }
                }
            });
            XposedHelpers.findAndHookMethod("org.telegram.ui.Cells.ChatActionCell", lpparam.classLoader, "getMessageObject", new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Object ts = param.thisObject;
                    Object currentMessageObject = XposedHelpers.getObjectField(ts, "currentMessageObject");
                    XposedHelpers.setBooleanField(XposedHelpers.getObjectField(currentMessageObject, "messageOwner"), "noforwards", false);
                    param.setResult(currentMessageObject);
                }
            });
            Class TLRPC_Chat = lpparam.classLoader.loadClass("org.telegram.tgnet.TLRPC$Chat");
            XposedHelpers.findAndHookMethod("org.telegram.messenger.MessagesController", lpparam.classLoader, "isChatNoForwards", TLRPC_Chat, XC_MethodReplacement.returnConstant(false));
            /**
             * 消息删除拦截
             */
            XposedHelpers.findAndHookMethod("org.telegram.messenger.MessagesController", lpparam.classLoader, "processUpdateArray", ArrayList.class, ArrayList.class, ArrayList.class, boolean.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Class TL_updateDeleteChannelMessages = lpparam.classLoader.loadClass("org.telegram.tgnet.TLRPC$TL_updateDeleteChannelMessages");
                    ArrayList<Object> messgeArr = (ArrayList<Object>) param.args[0];
                    for (Object item : messgeArr) {
//                        XposedBridge.log("------消息类型" + item.getClass());
                        if (item.getClass().equals(TL_updateDeleteChannelMessages)) {
//                            Object ts= param.thisObject;
//                            Object dialogMessage=XposedHelpers.getObjectField(ts, "dialogMessage");
//                            Object oo=XposedHelpers.callMethod(dialogMessage, "get",(-((long)XposedHelpers.getObjectField(item, "channel_id"))));
//                            XposedHelpers.setObjectField(oo, "messageText",XposedHelpers.getObjectField(oo, "messageText")+"测试");
//                            XposedHelpers.callMethod(dialogMessage, "put",(-((long)XposedHelpers.getObjectField(item, "channel_id"))),oo);
                            messgeArr.remove(item);
                        }
                    }
                    param.args[0] = messgeArr;
                }
            });
//            XposedHelpers.findAndHookMethod("org.telegram.messenger.MessagesStorage", lpparam.classLoader, "markMessagesAsDeletedInternal",long.class,ArrayList.class,boolean.class,boolean.class,new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    Object ts= param.thisObject;
//                    param.args[0]=0;
//                    param.args[1]=null;
//                    param.args[2]=false;
//                    param.args[3]=false;
//                }
//            });
//            XposedHelpers.findAndHookMethod("org.telegram.messenger.MessagesStorage", lpparam.classLoader, "markMessagesAsDeletedInternal",long.class,int.class,boolean.class,new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    Object ts= param.thisObject;
//                    param.args[0]=0;
//                    param.args[1]=0;
//                    param.args[2]=false;
//                }
//            });
            XposedHelpers.findAndHookMethod("org.telegram.ui.ChatActivity", lpparam.classLoader, "createMenu", View.class, boolean.class, boolean.class, float.class, float.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // this will be called after the clock was updated by the original method
                    //静态工具类初始化部分
                    thisObj = param.thisObject;

                    Class cell = lpparam.classLoader.loadClass("org.telegram.ui.ActionBar.ActionBarMenuSubItem");
                    Class TLRPC_Document = lpparam.classLoader.loadClass("org.telegram.tgnet.TLRPC$Document");

                    Class photoEntry = lpparam.classLoader.loadClass("org.telegram.messenger.MediaController$PhotoEntry");
                    photoEntryCt = XposedHelpers.findConstructorExact(photoEntry, int.class, int.class, long.class, String.class, int.class, boolean.class, int.class, int.class, long.class);
                    photoEntryCt.setAccessible(true);
                    chatActivityEnterViewField = thisObj.getClass().getDeclaredField("chatActivityEnterView");
                    chatActivityEnterViewField.setAccessible(true);
                    Class c = lpparam.classLoader.loadClass("org.telegram.ui.ActionBar.Theme$ResourcesProvider");
                    Field scrimPopupWindow = thisObj.getClass().getDeclaredField("scrimPopupWindow");//点击的弹框
                    popupWindow = scrimPopupWindow.get(param.thisObject);
                    Field selectedField = param.thisObject.getClass().getDeclaredField("selectedObject");//消息对象获取
                    selectedField.setAccessible(true);
                    Object selectedObject = selectedField.get(param.thisObject);//消息对象实例
                    //初始化结束

                    /**
                     * 创建菜单对象
                     */
                    Context context = (Context) XposedHelpers.callMethod(param.thisObject, "getParentActivity");//item初始化需要的对象
                    Object tResourcesProvider = XposedHelpers.callMethod(param.thisObject, "getResourceProvider");//item初始化需要的对象
                    Constructor ct = XposedHelpers.findConstructorExact(cell, Context.class, boolean.class, boolean.class, c);
                    ct.setAccessible(true);
                    Object newInstance = ct.newInstance(context, false, false, tResourcesProvider); //创建+1item

                    XposedHelpers.callMethod(newInstance, "setTextAndIcon", "复读机+1", mMsg_repeat);//设置名称和icon
                    XposedHelpers.callMethod(newInstance, "setColors", 0xffff0000, 0xffff0000);//设置颜色
                    XposedHelpers.callMethod(newInstance, "setItemHeight", 44);//不知道有没有用
                    XposedHelpers.callMethod(newInstance, "setMinimumWidth", XposedHelpers.callStaticMethod(AndroidUtilities, "dp", 200));//不知道有没有用 AndroidUtilities.dp(200)

                    //消息分类处理
                    sendMessge(selectedObject, (View) newInstance);//处理消息
                    //拦截复读GIF的时候一直添加历史记录
                    XposedHelpers.findAndHookMethod("org.telegram.messenger.MediaDataController", lpparam.classLoader, "addRecentGif", TLRPC_Document, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (isHookGIFADD) {
                                param.args[0] = null;
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            isHookGIFADD = false;
                        }
                    });
                    /**
                     * 获取布局层
                     */
                    ViewGroup vPop = (ViewGroup) XposedHelpers.callMethod(popupWindow, "getContentView");
                    Object fPpp = (Object) vPop.getChildAt(1);//存在表情评论索引
                    if (fPpp == null) {
                        fPpp = (Object) vPop.getChildAt(0);//没有表情评论索引
                    }
                    XposedHelpers.callMethod(fPpp, "addView", newInstance);//往布局里面添加我们的  复读机+1 item
                }
            });

        }
    }

    private void sendMessge(Object selectedObject, View newInstance) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        int selectedType = XposedHelpers.getIntField(selectedObject, "type");//消息类型
        String path = "132";//图片和GIF的路径
        Object chatActivityEnterObject = chatActivityEnterViewField.get(thisObj);
        ArrayList<Object> entries = new ArrayList<>();//图片AndGIF
//        XposedBridge.log("------消息类型" + selectedType);
        if (selectedType == 1) {
            Object currentPhotoObject = XposedHelpers.callStaticMethod(FileLoader, "getClosestPhotoSizeWithSize", XposedHelpers.getObjectField(selectedObject, "photoThumbs"), XposedHelpers.callStaticMethod(AndroidUtilities, "getPhotoSize"));
            if (currentPhotoObject != null) {
                File file = (File) XposedHelpers.callStaticMethod(FileLoader, "getPathToMessage", XposedHelpers.getObjectField(selectedObject, "messageOwner"));
                path = file.getPath();
            }
            Object entry = photoEntryCt.newInstance(0, 0, 0, path, 0, false, 0, 0, 0);
            entries.add(0, entry);

        }
        if (selectedType == 8) {
            //拦截复读GIF保存到历史消息
            path = XposedHelpers.callStaticMethod(FileLoader, "getPathToMessage", XposedHelpers.getObjectField(selectedObject, "messageOwner")).toString();
            String[] token = path.split("\\.");
            String pf = token[1];
            if (pf.equals("MOV")) {
                renameFile(path, token[0] + ".mp4");
                path = token[0] + ".mp4";
//                XposedBridge.log("------path" + path);
            }
            Object entry = photoEntryCt.newInstance(0, 0, 0, path, 0, false, 0, 0, 0);
            entries.add(0, entry);
        }

        newInstance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (selectedType) {
                    case 0:
                        XposedHelpers.callMethod(chatActivityEnterObject, "processSendingText", (SpannableString) XposedHelpers.getObjectField(selectedObject, "messageText"), true, 0);
                        break;
                    case 1:
                    case 8:
                        isHookGIFADD = true;
                        XposedHelpers.callMethod(thisObj, "sendPhotosGroup", entries, false, 0, false);
                        break;
                    case 13:
                    case 15:
                        if (XposedHelpers.getObjectField(XposedHelpers.getObjectField(selectedObject, "messageOwner"), "media") != null) {
                            XposedHelpers.callMethod(chatActivityEnterObject, "onStickerSelected", XposedHelpers.getObjectField(XposedHelpers.getObjectField(XposedHelpers.getObjectField(selectedObject, "messageOwner"), "media"), "document"), null, null, null, true, true, 0);
                        }
                        XposedHelpers.callMethod(chatActivityEnterObject, "onStickerSelected", XposedHelpers.getObjectField(selectedObject, "emojiAnimatedSticker"), null, null, null, true, true, 0);
                        break;
                }
                XposedHelpers.callMethod(popupWindow, "dismiss");
            }
        });
    }

    public static void renameFile(String fromFile, String newPath) {
//        File oleFile = new File(oldPath);
//        File newFile = new File(newPath);
//        //执行重命名
//        oleFile.renameTo(newFile);

        try
        {
            InputStream fosfrom = new FileInputStream(fromFile);
            OutputStream fosto = new FileOutputStream(newPath);
            byte bt[] = new byte[1024];
            int c;
            while ((c = fosfrom.read(bt)) > 0)
            {
                fosto.write(bt, 0, c);
            }
            fosfrom.close();
            fosto.close();


        } catch (Exception ex)
        {

        }
    }
}
