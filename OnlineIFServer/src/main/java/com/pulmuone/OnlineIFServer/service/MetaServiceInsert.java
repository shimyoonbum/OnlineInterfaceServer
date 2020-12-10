package com.pulmuone.OnlineIFServer.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.pulmuone.OnlineIFServer.common.Constants;
import com.pulmuone.OnlineIFServer.common.IFException;
import com.pulmuone.OnlineIFServer.common.ResponseStatus;
import com.pulmuone.OnlineIFServer.config.MetaConfig;
import com.pulmuone.OnlineIFServer.util.CUtil;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MetaServiceInsert {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    //Temporary
    private final static String restrictTemp = "restrict";
    private final static String batchListTemp = "batchList";

    private Connection conn = null;

    @Autowired
    JdbcTemplate jdbcTemplate;

	public String[] insertChildMap(Map metaMap, Map<String, Object> commonMap, Map<String, Object> paramMap) throws IFException {
		Map<String,Object> resultMap = new HashMap<String,Object>();
		List<Map> paramList = (List<Map>) paramMap.get(metaMap.get(Constants.path).toString());
		
		getSqlPart(metaMap, commonMap, paramMap, resultMap);

		beginTransaction();
		
		try {
			executeChildData(metaMap, commonMap, resultMap);
			endTransaction(true);
		} catch(Exception e) {
			endTransaction(false);
			throw e;
		}
		
		///lastTransaction(metaMap, paramMap);	//TODO; 임시
		
		resultMap.put("responseCode", ResponseStatus.OK.value());
		resultMap.put("responseMessage", ResponseStatus.OK.phrase());
		
		return new String[] {ResponseStatus.OK.value(), new Gson().toJson(resultMap)};
	}

	private void getSqlPart(Map metaMap, Map<String, Object> commonMap, Map<String, Object> paramMap, Map<String,Object> resultMap) throws IFException {
		List<Map> paramList = (List<Map>) paramMap.get(metaMap.get(Constants.path).toString());
		String sqlMessage = null;
		for(Map param : paramList) {
			buildSqlPart(metaMap, commonMap, param, resultMap);
			
	    	List<Map> children = (List<Map>) metaMap.get(MetaConfig.childrenMap);
			if(children == null)
	    		continue;
			
			for(Map child : children) {
				getSqlPart(child, commonMap, param, resultMap);
			}
		}
	}

	private void buildSqlPart(Map metaMap, Map<String, Object> commonMap, Map<String, Object> paramMap, Map<String,Object> resultMap) throws IFException {
		String path = metaMap.get(Constants.path).toString();
		String systemId = commonMap.get(Constants.systemId).toString();
		
		String restrict = (metaMap.get(restrictTemp) == null)? null : metaMap.get(restrictTemp).toString();
		String restrictColumn = null;
		List<String> restrictValues = new ArrayList<String>();
		boolean needRestrict = false;
		if(restrict!=null) {
			String[] restricts = restrict.split(":");
			String[] systems = restricts[0].split(",");
			List<String> restrictSystems = new ArrayList<String>();
			for(String s : systems)
				restrictSystems.add(s.trim());
			String[] restrictCondition = restricts[1].split("=");
			restrictColumn = restrictCondition[0].trim().toLowerCase();
			String[] values = restrictCondition[1].split(",");
			for(String v : values)
				restrictValues.add(v.trim());
			
			if(restrictSystems.contains(systemId))
				needRestrict = true;
		}
		Map sqlMap = (Map) resultMap.get(path);
		if(sqlMap==null) {
			sqlMap = new HashMap<String,Object>();
			resultMap.put(path, sqlMap);
		}

		List<List> batchList = (List<List>) sqlMap.get(batchListTemp);
		if(batchList==null) {
			batchList = new ArrayList<List>();
			sqlMap.put(batchListTemp, batchList);
		}
    	
		List<Object> valList = new ArrayList<Object>();

		List<Map> metaAttrs = (List<Map>) metaMap.get(MetaConfig.ifCols);
		if(metaAttrs==null)
			throw new IFException(ResponseStatus.FAIL, path+"의 ifmeta_attr정보가 없습니다.");
		
		boolean existJoinKey = false;
		boolean existJoinVal = false;
		
    	for(Map metaAttr : metaAttrs) {
    		if(metaAttr.get(Constants.mandatory)!=null && metaAttr.get(Constants.mandatory).equals(Constants.exceptMandatory))
    			continue;
    		
    		String dbCol = metaAttr.get(Constants.dbCol).toString();
    		Object val = paramMap.get(metaAttr.get(Constants.ifCol).toString());
    		valList.add(val);
    		
    		if(val==null && metaAttr.get(Constants.mandatory)!=null && metaAttr.get(Constants.mandatory).equals(Constants.systemMandatory))
				throw new IFException(ResponseStatus.MISSING, dbCol+" 값이 없습니다.");
    		if(metaAttr.get(Constants.mandatory)!=null && metaAttr.get(Constants.mandatory).equals(Constants.joinMandatory)) {
    			existJoinKey = true;
    			if(val!=null)
    				existJoinVal = true;
    		}
    		
			if(needRestrict && dbCol.equals(restrictColumn) && !restrictValues.contains(val.toString()))
				throw new IFException(ResponseStatus.ACCESS, restrictColumn+"에 입력 제한된 값("+val.toString()+")이 있습니다.");
			
    		///System.out.println(val);
    	}
    	
    	if(existJoinKey && !existJoinVal)
    		throw new IFException(ResponseStatus.MISSING,"JOIN항목의 값이 없습니다.");
    	
		batchList.add(valList);
	}

	private void executeChildData(Map metaMap, Map<String, Object> commonMap, Map<String,Object> resultMap) throws IFException {
		String sqlMessage;
		executeInsert(metaMap, commonMap, resultMap);
		
    	List<Map> children = (List<Map>) metaMap.get(MetaConfig.childrenMap);
		if(children == null)
			return;
		
		for(Map child : children) {
			executeChildData(child, commonMap, resultMap);
		}
	}
	
	private void executeInsert(Map metaMap, Map<String, Object> commonMap, Map<String, Object> resultMap) throws IFException {
		String path = metaMap.get(Constants.path).toString();
		Map sqlMap = (Map) resultMap.get(path);
		String target = (commonMap.get(Constants.target) == null)? null : commonMap.get(Constants.target).toString();
		
    	StringBuffer sbSql = new StringBuffer();
    	
		sbSql.append("insert into ");
		String ifTbl = metaMap.get(Constants.ifTbl).toString();
		sbSql.append(ifTbl);
		
		List<Map> metaAttrs = (List<Map>) metaMap.get(MetaConfig.ifCols);
		boolean needComma = false;
		sbSql.append(" (");

		if(target != null) {
			String[] targetRefs = target.split(",");
			for(String targetRef : targetRefs) {
				String[] targets = targetRef.split("-");
				if(!path.equals(targets[0].trim()))
					continue;
				
				sbSql.append(targets[1].trim()+"_FLG, "+targets[1].trim()+"_DAT ");
				needComma = true;
			}
		}
		
    	for(Map metaAttr : metaAttrs) {
    		if(metaAttr.get(Constants.mandatory)!=null && metaAttr.get(Constants.mandatory).equals(Constants.exceptMandatory))
    			continue;
    		
    		String dbCol = metaAttr.get(Constants.dbCol).toString();
    		if(needComma)
        		sbSql.append(",");
    		sbSql.append(dbCol);
    		needComma = true;
    	}
		sbSql.append(") ");
		
		needComma = false;
		sbSql.append("values (");
		if(target != null) {
			String[] targetRefs = target.split(",");
			for(String targetRef : targetRefs) {
				String[] targets = targetRef.split("-");
				if(!path.equals(targets[0].trim()))
					continue;
				
				sbSql.append("'Y', sysdate");
				needComma = true;
			}
		}
		
    	for(Map metaAttr : metaAttrs) {
    		if(metaAttr.get(Constants.mandatory)!=null && metaAttr.get(Constants.mandatory).equals(Constants.exceptMandatory))
    			continue;

    		String inConv= (metaAttr.get(Constants.inConv) == null)? "?" : metaAttr.get(Constants.inConv).toString().replaceAll("\\$", "?");
    		if(needComma)
        		sbSql.append(",");
    		sbSql.append(inConv);
    		needComma = true;
    	}
		sbSql.append(") ");
    	
		List<List> batchList = (List<List>) sqlMap.get(batchListTemp);
		
		Map<String, Object> attrs = new HashMap<String, Object>();
		List<Object[]> batchArgs = new ArrayList<Object[]>();
		//int attrSize = metaAttrs.size();
		
		for(List<Object> list : batchList) {
			Object[] oa = new Object[list.size()];	//[attrSize]
			int idx = 0;
			for(Object val : list)
				oa[idx++]= val;
			batchArgs.add(oa);
		}
		
		logger.debug(sbSql.toString()+";\n"+CUtil.convertListOfObjectArrayToJsonString(batchArgs));
		
		try {
			///jdbcTemplate.batchUpdate(sbSql.toString(), batchArgs);
			batchUpdate(sbSql.toString(), batchArgs);
			resultMap.remove(path);
		} catch (Exception e) {
			throw new IFException(ResponseStatus.FAIL, e.getMessage().replaceAll("\n", "").replaceAll("\"", "'"));
		}
	}

	private void beginTransaction() throws IFException {
		try {
			conn = jdbcTemplate.getDataSource().getConnection();
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			try { conn.rollback(); } catch (SQLException e1) { }
			throw new IFException(ResponseStatus.FAIL, e.getMessage().replaceAll("\n", "").replaceAll("\"", "'"));
		}
	}
	
	private void endTransaction(boolean isSuccess) throws IFException {
		try {
			if(isSuccess)
				conn.commit();
			else
				conn.rollback();
			
			conn.close();
		} catch (SQLException e) {
			try { conn.rollback(); } catch (SQLException e1) { }
			throw new IFException(ResponseStatus.FAIL, e.getMessage().replaceAll("\n", "").replaceAll("\"", "'"));
		}
	}
	
	private int[] batchUpdate(String sql, List<Object[]> batchArgs) throws IFException {
		PreparedStatement preparedStatement = null;
		
		try {
			preparedStatement = conn.prepareStatement(sql);
			for(Object[] args : batchArgs) {
				for(int i=1; i <= args.length; i++)
					preparedStatement.setObject(i, args[i-1]);

		        preparedStatement.addBatch();
		    }
			
			int[] affectedRecords = preparedStatement.executeBatch();
			
			return affectedRecords;
		} catch (SQLException e) {
			throw new IFException(ResponseStatus.FAIL, e.getMessage().replaceAll("\n", "").replaceAll("\"", "'"));
		} finally {
		    if(preparedStatement != null)
		        try { preparedStatement.close(); } catch (SQLException e) { }
		}
	}
	
	private void lastTransaction(Map metaMap, Map<String, Object> paramMap) throws IFException {
		int totalPage = ((Double) paramMap.get(Constants.totalPage)).intValue();
		int currentPage = ((Double) paramMap.get(Constants.currentPage)).intValue();
		if(currentPage < totalPage)
			return;
		
    	StringBuffer sbSql = new StringBuffer();
    	
		sbSql.append("update ");
		String ifTbl = metaMap.get(Constants.ifTbl).toString();
		sbSql.append(ifTbl);
		sbSql.append("  set itf_end='Y' ");
		sbSql.append("where ori_sys_seq=? ");

		String path = metaMap.get(Constants.path).toString();
		Map param = ((List<Map>) paramMap.get(path)).get(0);

		boolean found= false;
		Object transactionId = null;
		List<Map> metaAttrs = (List<Map>) metaMap.get(MetaConfig.ifCols);
    	for(Map metaAttr : metaAttrs) {
    		String ifCol = metaAttr.get(Constants.ifCol).toString();
    		if(!ifCol.equals("oriSysSeq"))
    			continue;
    		
    		transactionId = param.get(ifCol);
    		found= true;
    		break;
    	}
		
		if(!found)
			throw new IFException(ResponseStatus.FAIL, ifTbl+"의 ifmeta_attr에 트랜잭션 컬럼 정보가 없습니다.");
		
		logger.debug(sbSql.toString()+";\n"+transactionId);
		
		try {
			jdbcTemplate.update(sbSql.toString(), transactionId);
		} catch (Exception e) {
			throw new IFException(ResponseStatus.FAIL, e.getMessage().replaceAll("\n", "").replaceAll("\"", "'"));
		}
	}
}
