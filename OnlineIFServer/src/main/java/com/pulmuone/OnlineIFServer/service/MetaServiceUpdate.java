package com.pulmuone.OnlineIFServer.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.pulmuone.OnlineIFServer.common.Constants;
import com.pulmuone.OnlineIFServer.common.IFException;
import com.pulmuone.OnlineIFServer.common.ResponseStatus;
import com.pulmuone.OnlineIFServer.config.MetaConfig;
import com.pulmuone.OnlineIFServer.util.CUtil;
import com.pulmuone.OnlineIFServer.util.CamelListMap;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MetaServiceUpdate {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    //Temporary
    private final static String tableTemp = "table";
    private final static String valueTemp = "value";
    private final static String restrictTemp = "restrict";
    private final static String needRestrictTemp = "needRestrict";
    private final static String joinTableTemp = "joinTable";
    private final static String joinCondTemp = "joinCond";
    private final static String joinTableListTemp = "joinTableList";
    private final static String conditionTemp = "condition";
    private final static String setColumnsTemp = "setColumns";
    private final static String whereColumnsTemp = "whereColumns";

    @Autowired
    JdbcTemplate jdbcTemplate;
    
    private String flagOnly = null;
    private Connection conn = null;

    public String[] updateChildMap(Map parentMap, Map metaMap, Map<String, Object> commonMap, Map<String, Object> paramMap) throws IFException {
		Map<String,Object> resultMap = new HashMap<String,Object>();
		String sqlMessage = null;
		
		this.flagOnly = (String) metaMap.get(Constants.flagOnly);
		
		getSqlPart(parentMap, metaMap, commonMap, paramMap, resultMap);
		
		setChildSql(metaMap, resultMap);
		
		beginTransaction();
		
		try {
			updateChild(metaMap, commonMap, resultMap);
			endTransaction(true);
		} catch(Exception e) {
			endTransaction(false);
			throw e;
		}
		
		resultMap.remove(restrictTemp);
		resultMap.remove(needRestrictTemp);
		resultMap.remove(valueTemp);
		resultMap.remove(joinTableListTemp);
		resultMap.remove(joinCondTemp);
		//resultMap.remove(tableTemp);
		//resultMap.remove(joinTableTemp);
		
		if (resultMap.get("responseCode") != null)
			return new String[] {resultMap.get("responseCode").toString(), new Gson().toJson(resultMap)};
		
		resultMap.put("responseCode", ResponseStatus.OK.value());
		resultMap.put("responseMessage", ResponseStatus.OK.phrase());
		
		return new String[] {ResponseStatus.OK.value(), new Gson().toJson(resultMap)};
	}

	private void getSqlPart(Map parentMap, Map metaMap, Map<String, Object> commonMap, Map<String, Object> paramMap, Map<String,Object> resultMap) throws IFException {
		List<Map> paramList = (List<Map>) paramMap.get(metaMap.get(Constants.path).toString());
		if(paramList == null)
			return;
		
		for(Map param : paramList) {
			buildSqlPart(parentMap, metaMap, commonMap, param, resultMap);
			
	    	List<Map> children = (List<Map>) metaMap.get(MetaConfig.childrenMap);
			if(children == null)
				continue;
			
			for(Map child : children)
				getSqlPart(metaMap, child, commonMap, param, resultMap);
		}
	}
	
	private void buildSqlPart(Map parentMap, Map metaMap, Map<String, Object> commonMap, Map<String, Object> paramMap, Map<String,Object> resultMap) throws IFException {
		String path = metaMap.get(Constants.path).toString();
    	StringBuffer sbCol = new StringBuffer();
    	StringBuffer sbWhere = new StringBuffer();
    	List<Object> listValue = (List<Object>) resultMap.get(valueTemp);
		if(listValue==null) {
			listValue = new ArrayList<Object>();
			resultMap.put(valueTemp, listValue);
		}

    	//Map<String,Object> sqlMap = new HashMap<String,Object>();
		Map sqlMap = (Map) resultMap.get(path);
		if(sqlMap==null) {
			sqlMap = new HashMap<String,Object>();
			resultMap.put(path, sqlMap);
		}

		Map<String, Object> conditionMap = paramMap.get(conditionTemp)==null? new HashMap<String,Object>() : (Map<String, Object>) paramMap.get(conditionTemp);

    	List<List> setColumns = (List<List>) sqlMap.get(setColumnsTemp);
		if(setColumns==null) {
			setColumns = new ArrayList<List>();
			sqlMap.put(setColumnsTemp, setColumns);
		}
    	
    	List<List> whereColumns = (List<List>) sqlMap.get(whereColumnsTemp);
		if(whereColumns==null) {
			whereColumns = new ArrayList<List>();
			sqlMap.put(whereColumnsTemp, whereColumns);
		}
		
		List<Map> dataList = (List<Map>) sqlMap.get("dataList");
		if(dataList==null) {
			dataList = new ArrayList<Map>();
			sqlMap.put("dataList", dataList);
		}
		
		List<Object[]> setList = new ArrayList<Object[]>();
		List<Object[]> whereList = new ArrayList<Object[]>();
		Map<String, Object> dataMap = new HashMap<String,Object>();

		String alias = metaMap.get(Constants.alias).toString();
		String aliasParent = (parentMap.get(Constants.alias) == null)? null : parentMap.get(Constants.alias).toString();
		String parent = (metaMap.get(Constants.parent) == null)? null : metaMap.get(Constants.parent).toString();
		String target = (commonMap.get(Constants.target) == null)? null : commonMap.get(Constants.target).toString();
		String systemId = commonMap.get(Constants.systemId).toString();
		
		String restrict = (String) metaMap.get(Constants.restrict);
		String restrictCondition = null;
		boolean needRestrict = false;
		if(restrict!=null) {
			String[] restricts = restrict.split(":");
			String[] systems = restricts[0].split(",");
			List<String> restrictSystems = new ArrayList<String>();
			for(String s : systems)
				restrictSystems.add(s.trim());
			restrictCondition = restricts[1];
			
			if(restrictSystems.contains(systemId))
				needRestrict = true;
		}
		boolean needAnd = false;
		
		List<Map> metaAttrs = (List<Map>) metaMap.get(MetaConfig.ifCols);
		if(metaAttrs==null)
			throw new IFException(ResponseStatus.FAIL, path+"의 ifmeta_attr정보가 없습니다.");
		
		boolean needComma = false;
    	for(Map metaAttr : metaAttrs) {
    		String ifCol = metaAttr.get(Constants.ifCol).toString();
    		String useYn = metaAttr.get(Constants.useYn).toString();
    		///System.out.println("ifCol:"+ifCol+",useYn:"+useYn);
    		if(useYn==null || useYn.equals("N"))
    			continue;
    		
    		String dbCol = metaAttr.get(Constants.dbCol).toString();
    		String aCol = alias+"."+dbCol;
    		Object[] col = new Object[3];
    		String inConv= (metaAttr.get("inConv") == null)? "?" : metaAttr.get("inConv").toString().replaceAll("\\$", "?");
    		String outConv = (metaAttr.get("outConv") == null)? null : metaAttr.get("outConv").toString();
    		col[0] = aCol;
    		col[1] = inConv;

    		if(needComma)
    			sbCol.append(",");
    		
    		if(outConv==null)
    			sbCol.append(aCol);
    		else
    			sbCol.append(outConv.replaceAll("\\$", aCol)+" "+dbCol);
    		
    		needComma = true;

    		if(parent != null && metaAttr.get(Constants.parentCol) != null) {
    			String parentCol = aliasParent+"."+metaAttr.get(Constants.parentCol).toString();
        		if(needAnd)
        			sbWhere.append("\t   and ");
        		sbWhere.append("("+aCol+" is null or "+aCol+"="+parentCol+") \n");
        		needAnd = true;
    		} // if parent

    		if(paramMap.get(ifCol)!=null) {
    			Object val = paramMap.get(ifCol);
    			col[2] = val;
        		setList.add(col);
    		} else if(conditionMap.get(ifCol)!=null) {
    			col[2] = conditionMap.get(ifCol);
        		whereList.add(col);
    		}
    		
    		if(conditionMap.get(ifCol)!=null)
    			dataMap.put(dbCol, conditionMap.get(ifCol));
    		
    	} //for

    	if(this.flagOnly!=null && this.flagOnly.equals("Y") && setList.size() > 0)
    		throw new IFException(ResponseStatus.UPD_COND, "flag 업데이트만 가능합니다.");
    	
    	dataList.add(dataMap);
    	
		setColumns.add(setList);
		whereColumns.add(whereList);

		String ifTbl = metaMap.get(Constants.ifTbl).toString();
		sqlMap.put(tableTemp, ifTbl+" "+alias);
		sqlMap.put(joinTableTemp, ifTbl+" "+alias);
		sqlMap.put(joinCondTemp, sbWhere.toString());
		sqlMap.put(restrictTemp, restrictCondition);
		sqlMap.put(needRestrictTemp, needRestrict);
    	
		//System.out.println(sqlMap.toString()+";");
		
		resultMap.put(path, sqlMap);
	}

	private boolean setChildSql(Map metaMap, Map<String,Object> resultMap) {
		boolean hasNoChild= false;
		String path = metaMap.get(Constants.path).toString();
		Map sqlMap = (Map) resultMap.get(path);
		List<String> joinTableList = (List<String>) resultMap.get(joinTableListTemp);
		if(joinTableList==null) {
			joinTableList = new ArrayList<String>();
			resultMap.put(joinTableListTemp, joinTableList);
		}
		joinTableList.add((String) sqlMap.get(joinTableTemp));
		
		String joinCond = sqlMap.get(joinCondTemp).toString();
		resultMap.put(joinCondTemp, resultMap.get(joinCondTemp)==null || resultMap.get(joinCondTemp).toString().length()==0? joinCond : resultMap.get(joinCondTemp).toString()+"\t   and " + joinCond);
		
		String restrict = (String)sqlMap.get(restrictTemp);
		resultMap.put(restrictTemp, resultMap.get(restrictTemp)==null || resultMap.get(restrictTemp).toString().length()==0? restrict : resultMap.get(restrictTemp).toString()+" or " + restrict);
		boolean needRestrict = (boolean)sqlMap.get(needRestrictTemp);
		if(needRestrict)
			resultMap.put(needRestrictTemp, true);
		
    	List<Map> children = (List<Map>) metaMap.get(MetaConfig.childrenMap);
		if(children == null)
			return true;
		
		for(Map child : children) {
			hasNoChild = setChildSql(child, resultMap);
			//if(hasNoChild)
			//	return hasNoChild;
		}
		return false;
	}
	
	private void updateChild(Map metaMap, Map<String, Object> commonMap, Map<String,Object> resultMap) throws IFException {
		executeUpdate(metaMap, commonMap, resultMap);
		
    	List<Map> children = (List<Map>) metaMap.get(MetaConfig.childrenMap);
		if(children == null)
			return;
		
		for(Map child : children)
			updateChild(child, commonMap, resultMap);
	}
	
	private void executeUpdate(Map metaMap, Map<String, Object> commonMap, Map<String,Object> resultMap) throws IFException {
		String path = metaMap.get(Constants.path).toString();
		Map<String,Object> sqlMap = (Map<String,Object>) resultMap.get(path);
		//resultMap.remove(path);
		if(sqlMap==null)
			return;
		
		List<List> setColumns = (List<List>) sqlMap.get(setColumnsTemp);
		List<List> whereColumns = (List<List>) sqlMap.get(whereColumnsTemp);
		
		List<Object> value = (List<Object>) resultMap.get(valueTemp);
		Object[] values = new Object[value.size()];
		value.toArray(values);

		String table = sqlMap.get(tableTemp).toString();
		List<String> joinTableList = (List<String>) resultMap.get(joinTableListTemp);
		String joinCond = resultMap.get(joinCondTemp).toString();
		String restrict = (String) resultMap.get(restrictTemp);
		boolean needRestrict = resultMap.get(needRestrictTemp)==null? false: (boolean) resultMap.get(needRestrictTemp);

		Map<String,List> unAffectedMap = (Map<String,List>) resultMap.get(Constants.unAffected);
		if(unAffectedMap==null) {
			unAffectedMap = new HashMap<String,List>();
			resultMap.put(Constants.unAffected, unAffectedMap);
		}

		///TODO; 임시
		///where += " and itf_end='Y'";
		
    	StringBuffer sbSql = new StringBuffer();
		
    	sbSql.append("\nupdate "+table+"\n");

		Map<String, Object> attrs = new HashMap<String, Object>();
		for(List<Object[]> list : setColumns)
			for(Object[] col : list)
				attrs.put(col[0].toString(), col[1].toString());
		
		List<Object[]> setArgs = new ArrayList<Object[]>();
		List<Object[]> whereArgs = new ArrayList<Object[]>();
		
		boolean needComma = false;
		int seq= 0;
		sbSql.append(" set ");
		String target = (commonMap.get(Constants.target) == null)? null : commonMap.get(Constants.target).toString();
		boolean foundTarget = false;
		if(target != null) {
			String alias = metaMap.get(Constants.alias).toString();
			String[] targetRefs = target.split(",");
			for(String targetRef : targetRefs) {
				String[] targets = targetRef.split("-");
				if(!path.equals(targets[0].trim()))
					continue;
				
				sbSql.append(" "+alias+"."+targets[1].trim()+"_FLG='Y', "+alias+"."+targets[1].trim()+"_DAT=sysdate ");
				needComma = true;
				foundTarget = true;
			}
		}
		
		for (Entry<String, Object> entry : attrs.entrySet()) {
			if(needComma)
				sbSql.append(", ");
			sbSql.append(entry.getKey()+"="+entry.getValue()+" ");
			entry.setValue(seq++);
			needComma = true;
		}
		sbSql.append("\n");
		
    	String joinFrom = "";
    	boolean needJoin = false;
    	for(String joinTable : joinTableList) {
    		if(joinTable.equals(table))
    			continue;
			if(needJoin)
				joinFrom += ", ";
			joinFrom += joinTable;
    	    needJoin = true;
    	}
    	if(needJoin) {
        	sbSql.append("\nwhere exists (\n");
        	sbSql.append("\tselect 1\n");
        	sbSql.append("\t from "+joinFrom+"\n");
    	}
		
		int attrSize = attrs.keySet().size();
		if(attrSize==0 && !foundTarget) {
			int sizeWhere= 0;
			for(List<Object[]> list : whereColumns)
				sizeWhere+= list.size();
			if(sizeWhere > 0 && target == null)
				throw new IFException(ResponseStatus.MISSING, path+"의 업데이트 항목이 없습니다.");
			resultMap.remove(path);
			return;
		}
		
		for(List<Object[]> list : setColumns) {
			Object[] oa = new Object[attrSize];
			for(Object[] col : list) {
				int idx = (int) attrs.get(col[0].toString());
				oa[idx] = col[2];
			}
			setArgs.add(oa);
		}
		
		attrs = new HashMap<String, Object>();
		for(List<Object[]> list : whereColumns)
			for(Object[] col : list)
				attrs.put(col[0].toString(), col[1].toString());
		
		boolean needAnd = false;
		seq= 0;
		sbSql.append("\t where ");
		for (Entry<String, Object> entry : attrs.entrySet()) {
			if(needAnd)
				sbSql.append("\t   and ");
			sbSql.append("(? is null or "+entry.getKey()+" is null or "+entry.getKey()+"=?) \n");
			entry.setValue(seq++);
			needAnd = true;
		}
		
    	if(!joinCond.trim().equals(""))
    		sbSql.append("\t   and "+joinCond);
    	
    	if(needRestrict && restrict!=null)
    		sbSql.append("\t   and ("+restrict+")\n");
    	
    	if(needJoin)
        	sbSql.append(")\n");
    	else
    		sbSql.append("\n");
		
		attrSize = attrs.keySet().size();
		for(List<Object[]> list : whereColumns) {
			Object[] oa = new Object[attrSize*2];
			for(Object[] col : list) {
				int idx = (int) attrs.get(col[0].toString());
				oa[idx*2] = col[2];
				oa[idx*2+1] = col[2];
			}
			whereArgs.add(oa);
		}

		List<Object[]> batchArgs = new ArrayList<Object[]>();
		for(int i=0; i < setArgs.size(); i++) {
			Object[] setOa = setArgs.get(i);
			Object[] whereOa = whereArgs.get(i);
			
			Object[] oa = new Object[setOa.length+whereOa.length];
			int idx=0;
			for (Object value2 : setOa)
				oa[idx++] = value2;
			for (Object value2 : whereOa)
				oa[idx++] = value2;
			
			batchArgs.add(oa);
		}

		logger.debug(sbSql.toString()+";\n"+CUtil.convertListOfObjectArrayToJsonString(batchArgs)+"\n");

		
		try {
			///int[] iRet = jdbcTemplate.batchUpdate(sbSql.toString(), batchArgs);
			int[] iRet = batchUpdate(sbSql.toString(), batchArgs);
			
			List<Map> unAffectedList = new ArrayList<Map>();
			for(int idx= 0; idx < iRet.length; idx++) {
				if(iRet[idx] > 0)
					continue;
				
				List<Object[]> columnList= whereColumns.get(idx);
				Map<String, Object> dataMap = new HashMap<String,Object>();
				for(Object[] col : columnList) {
					String key = col[0].toString().replaceFirst("^.*\\.", "");
					Object val = col[2];
					dataMap.put(key, val);
				}
				unAffectedList.add(dataMap);
			}
			
			if(unAffectedList.size() > 0)
				unAffectedMap.put(path, unAffectedList);
			
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

}
