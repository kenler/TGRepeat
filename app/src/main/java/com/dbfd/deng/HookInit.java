package com.dbfd.deng;


import android.content.Context;
import android.content.res.XModuleResources;
import android.text.SpannableString;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookInit implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
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
        if (!resparam.packageName.equals("org.telegram.messenger.web"))
            return;
        XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
        mMsg_repeat = resparam.res.addResource(modRes, R.drawable.msg_repeat);

    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("org.telegram.messenger.web")) {
            FileLoader = lpparam.classLoader.loadClass("org.telegram.messenger.FileLoader");
            AndroidUtilities = lpparam.classLoader.loadClass("org.telegram.messenger.AndroidUtilities");
//            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    /**
//                     * hook截图禁止
//                     */
//                    XposedBridge.log("------attach加载 ");
//                    Class mBaseFragment = lpparam.classLoader.loadClass("org.telegram.ui.ActionBar.BaseFragment");
//                    XposedHelpers.findAndHookMethod("org.telegram.messenger.AndroidUtilities", lpparam.classLoader, "setFlagSecure",mBaseFragment, boolean.class, new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                            XposedBridge.log("------截图hook");
//                            if((boolean)param.args[1]){
//                                param.args[1]=false;
//
//                            }
//                        }
//                        @Override
//                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                            super.afterHookedMethod(param);
//
//                        }
//                    });
//                }
//            });
            XposedBridge.log("------Hook成功 " + lpparam.packageName);

            XposedHelpers.findAndHookMethod("org.telegram.messenger.AndroidUtilities", lpparam.classLoader, "updateFlagSecure", Window.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {



                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    super.afterHookedMethod(param);
//                    XposedBridge.log("------截图hook"+param.args[0]);
                    Window window =(Window) param.args[0];
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);

                }
            });
            XposedHelpers.findAndHookMethod("org.telegram.messenger.MessagesController", lpparam.classLoader, "processUpdateArray", ArrayList.class, ArrayList.class, ArrayList.class, boolean.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Class TL_updateDeleteChannelMessages = lpparam.classLoader.loadClass("org.telegram.tgnet.TLRPC$TL_updateDeleteChannelMessages");
                    ArrayList<Object> messgeArr = (ArrayList<Object>) param.args[0];
                    for (Object item : messgeArr) {
                        if (item.getClass().equals(TL_updateDeleteChannelMessages)) {
                            XposedBridge.log("---监听删除消息拦截");
                            messgeArr.remove(item);
                        }
                    }
                    param.args[0] = messgeArr;
                }
            });
            XposedHelpers.findAndHookMethod("org.telegram.ui.ChatActivity", lpparam.classLoader, "createMenu", View.class, boolean.class, boolean.class, float.class, float.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // this will be called before the clock was updated by the original method
                }

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
//            XposedHelpers.findAndHookMethod("android.view.Window", lpparam.classLoader, "setFlags",int.class,int.class, new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//
//
//
//                }
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
////                    super.afterHookedMethod(param);
//
//                    if((int)param.args[1]==WindowManager.LayoutParams.FLAG_SECURE){
//                        XposedBridge.log("------截图hook"+thisObj);
////                        XposedHelpers.callMethod(param.thisObject, "clearFlags",WindowManager.LayoutParams.FLAG_SECURE);
//                      Window window=(Window)  XposedHelpers.callMethod(thisObj, "getWindow");
//                      window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
//                    }
//
//                }
//            });

        }
    }
    private void sendMessge(Object selectedObject, View newInstance) throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, InstantiationException {
        int selectedType = XposedHelpers.getIntField(selectedObject, "type");//消息类型
        String path = "132";//图片和GIF的路径
        Object chatActivityEnterObject = chatActivityEnterViewField.get(thisObj);
        ArrayList<Object> entries = new ArrayList<>();//图片AndGIF
        XposedBridge.log("------消息类型" + selectedType);
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
}