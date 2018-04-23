
package interfaceApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.java.JGrapeSystem.rMsg;
import common.java.apps.appsProxy;
import common.java.database.dbFilter;
import common.java.file.uploadFileInfo;
import common.java.file.uploadFiles;
import common.java.image.imageHelper;
import common.java.interfaceModel.GrapeDBDescriptionModel;
import common.java.interfaceModel.GrapePermissionsModel;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.nlogger.nlogger;
import common.java.security.codec;
import common.java.session.session;
import common.java.store.store;
import common.java.string.StringHelper;
import common.java.time.timeHelper;
import io.netty.buffer.ByteBuf;

/**
 * 
 * [文件管理] <br> 
 *  
 * @version 1.0.0<br>
 * @CreateDate 2018年4月19日 <br>
 * @since v1.0.0<br>
 * @see interfaceApplication <br>
 */
public class Files {
	
	private JSONObject configString = null;
    private static AtomicInteger fileNO = new AtomicInteger(0);
    private String thumailPath = "\\File\\upload\\icon\\folder.ico";
    private GrapeTreeDBModel files;
    private JSONObject userInfo = null;
    private String currentWeb = null;
    private String pkStirng;

    public Files() {
    	
        files = new GrapeTreeDBModel();
        
        //数据模型
        GrapeDBDescriptionModel  gDbSpecField = new GrapeDBDescriptionModel ();
        gDbSpecField.importDescription(appsProxy.tableConfig("Files"));
        files.descriptionModel(gDbSpecField);
        
        //权限模型
        GrapePermissionsModel gperm = new GrapePermissionsModel();
		gperm.importDescription(appsProxy.tableConfig("Files"));
		files.permissionsModel(gperm);
		
		pkStirng = files.getPk();
		
		files.enableCheck();

        userInfo = (new session()).getDatas();
        if (userInfo != null && userInfo.size() > 0) {
            currentWeb = userInfo.getString("currentWeb"); // 当前用户所属网站id
        }
        
        configString = appsProxy.configValue();
		if (configString != null) {
			configString = JSONObject.toJSON(configString.getString("other"));
		}
    }

	/**
	 * 
	 * [上传文件   前端获取键为filepath的值，也就是上传到fastDFS的资源ID] <br> 
	 *  
	 * @author [南京喜成]<br>
	 * @param keyName 上传文件的name属性
	 * @return <br>
	 */
    @SuppressWarnings("unchecked")
	public String FileUpload(String keyName){
    	JSONObject Info = new JSONObject();
        if(StringHelper.InvaildString(keyName)){
        	return resultMsg(2,"");
        }
        uploadFiles uploadFiles = new uploadFiles(keyName);
        if(StringHelper.InvaildString(uploadFiles.toString())){
        	return resultMsg(3,"");
        }
        uploadFileInfo fileInfo = uploadFiles.getFileInfo();
        String oldName = fileInfo.getClientName();//原有的文件名称
        long fileSize = fileInfo.getFileSize();//文件的长度
        String fileType = fileInfo.getFileType();//文件类型
        Info.put("fileoldname", oldName);
        Info.put("filetype", fileType);
        Info.put("size", fileSize);
        //文件上传
        JSONObject uploadFile = uploadFile(fileInfo,Info);
        if(uploadFile != null){
        	if(!uploadFile.containsKey("errorCode")){
        		//网站
        		uploadFile.put("wbid", currentWeb);
        		//获取网页缩略图
        		uploadFile = addThumbnailInfo(uploadFile);
        		//添加到数据库中
        		Object insertEx = files.data(uploadFile).autoComplete().insertEx();
        		if(!StringHelper.InvaildString(insertEx.toString())){
        			return resultMsg(1,"文件上传成功");
        		} else {
        			return resultMsg(99,"文件上传失败");
        		}
        	} else {
        		//文件上传失败
        		return resultMsg(99,"文件上传失败");
        	}
        }
        return resultMsg(99,"文件上传失败");
    }
    
    /**
     * 文件修改，即重命名
     * 
     * @param fid
     * @param fileInfo
     * @return
     */
    public String fileUpdate(String fid, String fileInfo) {
        if(StringHelper.InvaildString(fileInfo)){
        	return resultMsg(2,"");
        }
        fileInfo = codec.DecodeFastJSON(fileInfo);
        JSONObject object = JSONObject.toJSON(fileInfo);
        if (!StringHelper.InvaildString(object.toString())) {
            boolean updateEx = files.eq(pkStirng, fid).data(object).updateEx();
            if(updateEx){
            	return resultMsg(1,"文件名修改成功");
            } else {
            	return resultMsg(99,"文件名修改失败");
            }
        }
        return resultMsg(99,"文件名修改失败");
    }
    
    /**
     * 获取文件内容
     * 
     * @param fid
     * @return
     */
    public String getWord(String fid) {
        JSONObject object = getFileInfo(fid);
        if(!StringHelper.InvaildString(object.toString())){
        	String resourceID = (String)object.get("filepath");
        	if(!StringHelper.InvaildString(resourceID)){
        		store store = new store();
				String safeResourceUrl = store.getSafeResourceUrl(resourceID);
				return safeResourceUrl;
        	} else {
        		return resultMsg(99,"获取文件失败");
        	}
        } else {
        	return resultMsg(99,"获取文件失败");
        }
    }
    
    /**
     * 新增文件夹
     * 
     * @param fileInfo
     * @return TODO
     */
    @SuppressWarnings("unchecked")
    public String addFolder(String fileInfo) {
    	if(StringHelper.InvaildString(fileInfo)){
    		return resultMsg(2,"");
    	}
    	JSONObject temp ;
    	fileInfo = codec.DecodeFastJSON(fileInfo);
        JSONObject object = JSONObject.toJSON(fileInfo);
        object.put("ThumbnailImage", thumailPath);
        object.put("filetype", 0);
        String info = (String) files.data(object).autoComplete().insertEx();
        if (!StringHelper.InvaildString(info)) {
            temp = getFileInfo(info);
            if (temp == null || temp.size() <= 0) {
                return resultMsg(99,"新增文件夹失败");
            } else {
                return resultMsg(1,"新增文件夹成功");
            }
        } else {
        	return resultMsg(99,"新增文件夹失败");
        }
        
    }
    
    
    /**
     * 分页显示文件信息
     * 
     * @param idx
     * @param pageSize
     * @param fileInfo  
     *            为空或者为null，显示所有的文件信息
     * @return
     */
    public String PageBy(int idx, int pageSize, String fileInfo) {
        long total = 0;
        JSONArray array = null;
        if (!StringHelper.InvaildString(fileInfo)) {
        	fileInfo = codec.DecodeFastJSON(fileInfo);
            JSONArray condArray = buildCond(fileInfo);
            if (condArray != null && condArray.size() > 0) {
                files.where(condArray);
            } else {
                return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
            }
        }
        if (!StringHelper.InvaildString(currentWeb)) {
            files.eq("wbid", currentWeb);
            total = files.dirty().count();
            array = files.asc("filetype").desc("time").page(idx, pageSize);
        }
        return rMsg.netPAGE(idx, pageSize, total, array);
    }
    
    /**
     * 将文件移动到某个文件夹内
     * 
     * @param fids
     * @param folderid
     * @return
     */
    public String fileUpdateBatch(String fids, String folderid) {
        // 验证文件夹是否存在
        if (getFileInfo(folderid) == null) {
            return resultMsg(2,"");
        }
        if (!StringHelper.InvaildString(fids)) {
            String FileInfo = "{\"fatherid\":\"" + folderid + "\"" + "}";
            int code = updates(fids, JSONObject.toJSON(FileInfo));
            if(code > 0){
            	return resultMsg(1,"移至文件成功");
            } else {
            	return resultMsg(99,"移至文件失败");
            }
        } else {
        	 return resultMsg(2,"");
        }
    }
    
    /**
     * 删除文件
     * 
     * @param fid 文件的id
     * @return
     */
    public String deleteFile(String fid) {
        if(!StringHelper.InvaildString(fid)){
        	boolean deleteEx = files.deleteEx();
        	if(deleteEx){
        		return resultMsg(1,"删除文件成功");
        	} else {
        		return resultMsg(99,"删除文件失败");
        	}
        } else {
        	return resultMsg(2,"文件信息为空");
        }
    }
    
    /**
     * 
     * [批量删除文件] <br> 
     *  
     * @param fides
     * @return <br>
     */
    public String batchDeleteFiles(String fides) {
        if(!StringHelper.InvaildString(fides)){
        	long deleteAllEx = files.deleteAllEx();
        	if(deleteAllEx > 0){
        		return resultMsg(1,"删除文件成功");
        	} else {
        		return resultMsg(99,"删除文件失败");
        	}
        } else {
        	return resultMsg(2,"文件信息为空");
        }
    }
    
    /**
     * 批量修改文件
     * 
     * @return
     */
    private int updates(String fids, JSONObject FileInfo) {
        int code = 99;
        if (FileInfo != null && FileInfo.size() > 0) {
            String[] value = fids.split(",");
            files.or();
            for (String fid : value) {
                if (StringHelper.InvaildString(fid)) {
                    files.eq(pkStirng, fid);
                }
            }
        }
        code = files.data(FileInfo).updateAll() > 0 ? 0 : 99;
        return code;
    }
    
    
    /**
	 * 整合参数，将JSONObject类型的参数封装成JSONArray类型
	 * 
	 * @param object
	 * @return
	 */
	private JSONArray buildCond(String Info) {
		String key;
		Object value;
		JSONArray condArray = null;
		JSONObject object = JSONObject.toJSON(Info);
		dbFilter filter = new dbFilter();
		if (object != null && object.size() > 0) {
			for (Object object2 : object.keySet()) {
				key = object2.toString();
				value = object.get(key);
				filter.eq(key, value);
			}
			condArray = filter.build();
		}else{
		    condArray = JSONArray.toJSONArray(Info);
		}
		return condArray;
	}
    
    
    /**
     * 
     * [添加缩列图信息] <br> 
     *  
     * @param uploadFile 文件信息
     * @return <br>
     */
    @SuppressWarnings("unchecked")
	private JSONObject addThumbnailInfo(JSONObject uploadFile) {
		
        String type = "";
        if (uploadFile != null && uploadFile.size() > 0) {
            if (uploadFile.containsKey("type")) {
                type = uploadFile.getString("type");
                uploadFile.put("filetype", getType(type));
            }
            // 获取缩略图
            uploadFile = getThumbnail(uploadFile);
        }
        return uploadFile;
		
	}
    
    
    /**
     * 查询文件信息
     * 
     * @param fid
     * @return
     */
    private JSONObject getFileInfo(String fid) {
        JSONObject object = null;
        if (!StringHelper.InvaildString(fid)) {
            object = files.eq(pkStirng, fid).find();
        }
        return object;
    }
    
    /**
     * 图片类型文件，显示图片缩略图，视频类型的文件显示视频缩略图
     * 
     * @project GrapeFile
     * @package interfaceApplication
     * @file Files.java
     * 
     * @param filetype
     * @return
     *
     */
    @SuppressWarnings("unchecked")
    private JSONObject getThumbnail(JSONObject object) {
        String filepath = "";
        String extName = "";
        if (object != null && object.size() > 0) {
            if (object.containsKey("filepath")) {
                filepath = object.getString("filepath");
            }
            //获取文件的后缀
        	String fileOldname = (String)object.get("fileoldname");
        	extName = getExt(fileOldname);
        }
        //文件的缩列图
        int width = Integer.parseInt(getFilePath("width"));
        int height = Integer.parseInt(getFilePath("height"));
        String thumb = imageHelper.thumb(filepath, height, width, extName);
        object.put("ThumbnailImage", thumb);
        return object;
    }
    
    /**
     * 获取上传文件类型
     * 
     * @project GrapeFile
     * @package interfaceApplication
     * @file Files.java
     * 
     * @param type
     * @return 1：图片；2：视频；3：文档:txt,word,excel,ppt。。。。。；4：音频；5：其他
     *
     */
    private int getType(String type) {
        int filetype = 5;
        if (!type.equals("")) {
            type = type.toLowerCase();
            if (type.contains("image")) {
                filetype = 1;
            }
            if (type.contains("video")) {
                filetype = 2;
            }
            if (type.contains("application")) {
                filetype = 3;
            }
        }
        return filetype;
    }

	/**
     * 
     * [文件上传] <br> 
     *  
     * @param fileInfo
     * @param Info
     * @return <br>
     */
    @SuppressWarnings("unchecked")
	private JSONObject uploadFile(uploadFileInfo fileInfo,JSONObject Info ){
    	 boolean flag = true;
         String addResource = "" ;
    	//获取文件的后缀
    	String fileOldname = (String)Info.get("fileoldname");
    	String extName = getExt(fileOldname);
    	//文件的新名称
    	String newName = timeHelper.nowMillis() + "." + extName; 
        String Date = timeHelper.stampToDate(timeHelper.nowMillis()).split(" ")[0];
        String dirpath = getFilePath("filepath") + Date; // 上传文件存储地址
        if (!new File(dirpath).exists()) {
            new File(dirpath).mkdir();
        }
        String filepath = dirpath + "//" + newName;
        File file = new File(filepath);
        try {
            while (flag) {
                if (file.exists()) {
                	newName = timeHelper.nowMillis() + getUnqueue() + "." + extName;
                    filepath = dirpath + newName;
                } else {
                    flag = false;
                }
            }
            if (fileInfo.isBuff()) {
                FileOutputStream fin = null;
                try {
                    fin = new FileOutputStream(file);
                    ByteBuf buff = (ByteBuf) fileInfo.getLocalBytes();
                    buff.readBytes(fin, buff.readableBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                    addResource = "";
                } finally {
                    fin.close();
                }
            } else {
                File src = fileInfo.getLocalFile();
                store store = new store();
                addResource = store.addResource(src);
                //通过资源的id来获取资源的路径
                if(!StringHelper.InvaildString(addResource)){
                	//删除本地文件
                	src.deleteOnExit();
                }
                Info.put("filenewname", newName);
                Info.put("filepath", addResource);
                Info.put("fileextname", getExt(extName));
            }
        } catch (Exception e) {
            nlogger.logout(e);
            Info.put("errorCode", 0);
        }
        return Info;
    	
    }
    
    
    /**
     * 
     * [返回数据说明] <br> 
     *  
     * @author <br>
     * @return <br>
     */
    private String resultMsg(int code,String msg){
    	if(code == 1){
    		return rMsg.netMSG(code, msg);
    	} else if(code == 2) {
    		return rMsg.netMSG(code, "参数缺失");
    	} else if(code == 3){
    		return rMsg.netMSG(code, "参数解析失败");
    	} else if(code == 99){
    		return rMsg.netMSG(code, msg);
    	}
    	return null;
    }
    
    /**
     * 获取文件扩展名
     * 
     * @project GrapeFile
     * @package interfaceApplication
     * @file Files.java
     * 
     * @param name
     * @return
     *
     */
    private String getExt(String name) {
        String extname = "";
        if (name.contains(".")) {
            extname = name.substring(name.lastIndexOf(".") + 1);
        }
        return extname;
    }
    
    /**
   	 * 获取configName字段中的配置信息
   	 * @param key
   	 * @return
   	 */
   	private String getFilePath(String key) {
   		JSONObject object = null;
   		String value = "";
   		if (configString !=null && configString.size() > 0) {
   			object = configString.getJson("upload");
   		}
   		if (object != null && object.size() > 0) {
   		    if (object.containsKey(key)) {
   		        value = object.getString(key);
               }
   		}
   		return value;
   	}
   	
   	private String getUnqueue() {
		return (new Integer(fileNO.incrementAndGet())).toString();
	}
}

