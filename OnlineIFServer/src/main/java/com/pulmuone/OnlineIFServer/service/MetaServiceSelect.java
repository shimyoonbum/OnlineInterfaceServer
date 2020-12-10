package com.pulmuone.OnlineIFServer.service;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class MetaServiceSelect {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    //Temporary
    private final static String tableTemp = "table";
    private final static String columnTemp = "column";
    private final static String whereTemp = "where";
    private final static String orderTemp = "order";
    private final static String valueTemp = "value";
    private final static String restrictTemp = "restrict";

    @Autowired
    JdbcTemplate jdbcTemplate;

    public String[] selectChildMap(Map parentMap, Map metaMap, Map<String, Object> commonMap, Map<String, Object> paramMap) throws IFException {
		Map<String,Object> resultMap = new HashMap<String,Object>();
		String sqlMessage = null;
		
		Map<String, Object> filterMap = null;
		if(commonMap.get(Constants.filter)!=null) {
			try {
				filterMap = new Gson().fromJson(commonMap.get(Constants.filter).toString(), Map.class);
			} catch (Exception e) {
				throw new IFException(ResponseStatus.SEARCH, "_filter 조건의 json 형식에 오류가 있습니다.");
			}
			String display = commonMap.get(Constants.filter)==null? null: commonMap.get(Constants.filter).toString();
		}
		
		getSqlPart(parentMap, metaMap, commonMap, filterMap, paramMap, resultMap);
		
		setChildSql(metaMap, resultMap);
		
		getChildData(metaMap, commonMap, resultMap);
		
		setChildData(metaMap, resultMap);
		
		resultMap.remove(restrictTemp);
		resultMap.remove(whereTemp);
		resultMap.remove(orderTemp);
		resultMap.remove(valueTemp);
		resultMap.remove(tableTemp);
		
		if (resultMap.get("responseCode") != null)
			return new String[] {resultMap.get("responseCode").toString(), new Gson().toJson(resultMap)};
		
		resultMap.put("responseCode", ResponseStatus.OK.value());
		resultMap.put("responseMessage", ResponseStatus.OK.phrase());
		
		return new String[] {ResponseStatus.OK.value(), new Gson().toJson(resultMap)};
	}

	private void getSqlPart(Map parentMap, Map metaMap, Map<String, Object> commonMap, Map<String, Object> filterMap, Map<String, Object> paramMap, Map<String,Object> resultMap) throws IFException {
		buildSqlPart(parentMap, metaMap, commonMap, filterMap, paramMap, resultMap);
		
    	List<Map> children = (List<Map>) metaMap.get(MetaConfig.childrenMap);
		if(children == null)
			return;
		
		for(Map child : children)
			getSqlPart(metaMap, child, commonMap, filterMap, paramMap, resultMap);
	}
	
	private void buildSqlPart(Map parentMap, Map metaMap, Map<String, Object> commonMap, Map<String, Object> filterMap, Map<String, Object> paramMap, Map<String,Object> resultMap) throws IFException {
    	StringBuffer sbCol = new StringBuffer();
    	StringBuffer sbWhere = new StringBuffer();
    	StringBuffer sbOrderBy = new StringBuffer();
    	List<Object> listValue = null;
		if(resultMap.get(valueTemp)==null) {
			listValue = new ArrayList<Object>();
			resultMap.put(valueTemp, listValue);
		} else
			listValue = (List<Object>) resultMap.get(valueTemp);
    	Map<String,Object> sqlMap = new HashMap<String,Object>();
    	
		String alias = metaMap.get(Constants.alias).toString();
		String aliasParent = (parentMap.get(Constants.alias) == null)? null : parentMap.get(Constants.alias).toString();
		String parent = (metaMap.get(Constants.parent) == null)? null : metaMap.get(Constants.parent).toString();
		String path = metaMap.get(Constants.path).toString();
		String target = (commonMap.get(Constants.target) == null)? null : commonMap.get(Constants.target).toString();
		String order = (commonMap.get(Constants.order) == null)? null : commonMap.get(Constants.order).toString();
		String systemId = commonMap.get(Constants.systemId).toString();
		
		String restrict = (metaMap.get(Constants.restrict) == null)? null : metaMap.get(Constants.restrict).toString();
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
		
		if(target != null) {
			String[] targetRefs = target.split(",");
			for(String targetRef : targetRefs) {
				String[] targets = targetRef.split("-");
				if(!path.equals(targets[0].trim()))
					continue;
				
				sbWhere.append("("+alias+"."+targets[1].trim()+"_FLG is null or "+alias+"."+targets[1].trim()+"_FLG"+" <> 'Y') ");
				needAnd = true;
			}
		}
		
		List<String[]> orderList = new ArrayList<String[]>();
		if(order != null) {
			String[] orderRefs = order.split(",");
			for(String orderRef : orderRefs) {
				String[] orders = orderRef.split("-");
				if(!path.equals(orders[0].trim()))
					continue;
				orderList.add(orders);
			}
		}
		
		List<String> filterCols = null;
		if(filterMap!=null) {
			filterCols = (List<String>) filterMap.get(path);
			if(filterCols.size()==0)
				throw new IFException(ResponseStatus.SEARCH, "_filter 조건의 "+path+"에 지정된 컬럼이 없습니다.");
		}
		
		List<Map> metaAttrs = (List<Map>) metaMap.get(MetaConfig.ifCols);
		if(metaAttrs==null)
			throw new IFException(ResponseStatus.FAIL, path+"의 ifmeta_attr정보가 없습니다.");
		boolean needComma = false;
		boolean needOrderByComma = false;
		
    	for(Map metaAttr : metaAttrs) {
    		String ifCol = metaAttr.get(Constants.ifCol).toString();
    		if(filterCols!=null && !filterCols.contains(ifCol)) {
        		if(metaAttr.get(Constants.mandatory)!=null && metaAttr.get(Constants.mandatory).equals(Constants.joinMandatory))
        			throw new IFException(ResponseStatus.SEARCH, "_filter 조건의 "+path+"에  JOIN키 컬럼("+ifCol+")이 없습니다.");
        		
    			continue;
    		}
    		
    		String useYn = metaAttr.get(Constants.useYn).toString();
    		///System.out.println("ifCol:"+ifCol+",useYn:"+useYn);
    		if(useYn==null || useYn.equals("N"))
    			continue;
    		
    		String dbCol = metaAttr.get(Constants.dbCol).toString();
    		String aCol = alias+"."+dbCol;
    		String inConv = (metaAttr.get(Constants.inConv) == null)? null : metaAttr.get(Constants.inConv).toString();
    		String outConv = (metaAttr.get("outConv") == null)? null : metaAttr.get("outConv").toString();

    		if(needComma)
    			sbCol.append(",");
    		
    		if(outConv==null)
    			sbCol.append(aCol);
    		else
    			sbCol.append(outConv.replaceAll("\\$", aCol)+" "+dbCol);
    		
    		needComma = true;

    		if(!ifCol.equals(Constants.target) && !ifCol.equals(Constants.page) && commonMap.get(ifCol)!=null) { //search condition
    			String values = commonMap.get(ifCol).toString();
    			String val = null;
    			String pathInd = null;
    			int idx = values.indexOf("-");
    			if(idx > 0) {
    				pathInd = values.substring(0, idx);
    				val = values.substring(idx+1).trim();
    			} else
    				val = values.trim();
    			
    			if(pathInd==null || pathInd.equals(path)) {
            		if(needAnd)
            			sbWhere.append(" and ");
            		needAnd = true;
            		
    				idx = values.indexOf("~");
        			if(idx > 0) {
        				String fromVal = values.substring(0, idx);
        				String toVal = values.substring(idx+1).trim();
                		if(inConv==null)
                			sbWhere.append(aCol+" between ? and ? ");
                		else
                			sbWhere.append(aCol+" between "+inConv.replaceAll("\\$", "?")+" and "+inConv.replaceAll("\\$", "?")+" ");
                		listValue.add(fromVal);
                		listValue.add(toVal);
        			} else {
                		if(inConv==null) {
                			if(val.startsWith("_in")) {
                				val = val.substring(3, val.length());
                				sbWhere.append(aCol+" in "+val+" ");
                			} else
                			if(val.indexOf("%") < 0) {
                				sbWhere.append(aCol+"=? ");
                    			listValue.add(val);
                			}
                			else {
                				sbWhere.append(aCol+" like ? ");
                    			listValue.add(val);
                			}
                		} else {
                			sbWhere.append(aCol+"="+inConv.replaceAll("\\$", "?")+" ");
                			listValue.add(val);
                		}
        			}
    			}
    		} // if target && !page
    		
    		if(parent != null && metaAttr.get(Constants.parentCol) != null) {
    			String parentCol = aliasParent+"."+metaAttr.get(Constants.parentCol).toString();
        		if(needAnd)
        			sbWhere.append("and ");
        		sbWhere.append("("+aCol+" is null or "+aCol+"="+parentCol+") ");
        		needAnd = true;
    		} // if parent
    		
    		if(orderList.size() > 0) {
    			for(String[] orders : orderList) {
    				if(!ifCol.equals(orders[1].trim()))
    					continue;
    				
    				if(needOrderByComma)
    					sbOrderBy.append(",");
    				sbOrderBy.append(aCol+" "+orders[2]);
    				needOrderByComma = true;
    			}
    		}
    	} //for
		
		String ifTbl = metaMap.get(Constants.ifTbl).toString();
		sqlMap.put(tableTemp, ifTbl+" "+alias);
		sqlMap.put(columnTemp, sbCol.toString());
		sqlMap.put(whereTemp, sbWhere.toString());
		sqlMap.put(orderTemp, sbOrderBy.toString());
		sqlMap.put(restrictTemp, restrictCondition);
    	
		//System.out.println(sqlMap.toString()+";");
		
		resultMap.put(path, sqlMap);
	}

	private boolean setChildSql(Map metaMap, Map<String,Object> resultMap) {
		boolean hasNoChild= false;
		String path = metaMap.get(Constants.path).toString();
		Map sqlMap = (Map) resultMap.get(path);
		String table = (String) sqlMap.get(tableTemp);
		resultMap.put(tableTemp, resultMap.get(tableTemp)==null? table : resultMap.get(tableTemp).toString()+"," + table);
		
		String where = (String) sqlMap.get(whereTemp);
		resultMap.put(whereTemp, resultMap.get(whereTemp)==null || resultMap.get(whereTemp).toString().length()==0? where : resultMap.get(whereTemp).toString()+" and " + where);
		
		String order = (String) sqlMap.get(orderTemp);
		if(order!=null && order.length() > 0)
			resultMap.put(orderTemp, resultMap.get(orderTemp)==null || resultMap.get(orderTemp).toString().length()==0? order : resultMap.get(orderTemp).toString()+ ", " + order);
		
		String restrict = (String) sqlMap.get(restrictTemp);
		resultMap.put(restrictTemp, resultMap.get(restrictTemp)==null || resultMap.get(restrictTemp).toString().length()==0? restrict : resultMap.get(restrictTemp).toString()+" or " + restrict);
		
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
	
	private void getChildData(Map metaMap, Map<String, Object> commonMap, Map<String,Object> resultMap) throws IFException {
		executeSelect(metaMap, commonMap, resultMap);
		
    	List<Map> children = (List<Map>) metaMap.get(MetaConfig.childrenMap);
		if(children == null)
			return;
		
		for(Map child : children)
			getChildData(child, commonMap, resultMap);
	}
	
	private void executeSelect(Map metaMap, Map<String, Object> commonMap, Map<String,Object> resultMap) throws IFException {
		String path = metaMap.get(Constants.path).toString();
		Map<String,Object> sqlMap = (Map<String,Object>) resultMap.get(path);
		String column = sqlMap.get(columnTemp).toString();
		
		List<Object> value = (List<Object>) resultMap.get(valueTemp);
		Object[] values = new Object[value.size()];
		value.toArray(values);

		String table = resultMap.get(tableTemp).toString();
		String where = resultMap.get(whereTemp).toString();
		String order = (String) resultMap.get(orderTemp);
		String restrict = (String) resultMap.get(restrictTemp);
		
		///TODO; 임시
		///where += " and itf_end='Y'";
		
    	String sql = "select distinct "+column+" from "+table;
    	if(!where.trim().equals(""))
    		sql += " where "+where;
    	if(order!=null && order.length() > 0)
    		sql += " order by "+order;
    	
    	if(restrict!=null)
    		sql += "   and ("+restrict+")";
    	
     	String sqlCount = "select count(*) cnt from ("+sql+")";
    	
		try {
			if(metaMap.get(Constants.parent)==null) {
				//System.out.println(sqlCount+";");
				Map<String, Object> countMap = jdbcTemplate.queryForMap(sqlCount, values);
				
				int pageSize = metaMap.get(Constants.page)==null? 100: ((BigDecimal) metaMap.get(Constants.page)).intValue();
				int totalPage = (((BigDecimal) countMap.get("cnt")).intValue()-1)/pageSize+1;
				resultMap.put(Constants.totalPage, totalPage);
				int searchPage = (commonMap.get(Constants.page) == null)? 1 : Integer.parseInt(commonMap.get(Constants.page).toString());
				resultMap.put(Constants.currentPage, searchPage);
				resultMap.put(Constants.pageSize, pageSize);
				
		    	sql = "select * from (select rownum rn_, x.* from ("+sql+") x) where rn_ > "+((searchPage-1)*pageSize)+" and rn_ <= "+(searchPage*pageSize);
			}
			
			logger.debug(sql+";\n"+CUtil.convertObjectArrayToJsonString(values)+"\n");
			
			List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, values);
			if(list.size() == 0) {
				resultMap.put(metaMap.get(Constants.path).toString(), list);
				resultMap.put("responseCode", ResponseStatus.NOT_FOUND.value());
				resultMap.put("responseMessage", ResponseStatus.NOT_FOUND.phrase());
			} else {
				List<CamelListMap> cameList = new ArrayList<CamelListMap>();
				for(Map<String, Object> elem : list)
					cameList.add(CamelListMap.toCamelListMap(elem));
				
				resultMap.put(metaMap.get(Constants.path).toString(), cameList);
			}
		} catch (Exception e) {
			throw new IFException(ResponseStatus.FAIL, e.getMessage().replaceAll("\n", "").replaceAll("\"", "'"));
		}
	}

	private boolean setChildData(Map metaMap, Map<String,Object> resultMap) {
		boolean hasNoChild= false;
		String path = metaMap.get(Constants.path).toString();
		if(metaMap.get(Constants.parent)!=null) {
			List<CamelListMap> listSelf = (List<CamelListMap>) resultMap.get(path);
			for(Map<String, Object> mapSelf : listSelf) {
				linkToParent(metaMap, resultMap, mapSelf, path);
			}
			resultMap.remove(path);
		}
		
    	List<Map> children = (List<Map>) metaMap.get(MetaConfig.childrenMap);
		if(children == null)
			return true;
		
		for(Map child : children) {
			hasNoChild = setChildData(child, resultMap);
			//if(hasNoChild)
			//	return hasNoChild;
		}
		return false;
	}
	
	private void linkToParent(Map metaMap, Map<String,Object> resultMap, Map mapSelf, String path) {
		String parent = metaMap.get(Constants.parent).toString();
		List<Map> metaAttrs = (List<Map>) metaMap.get(MetaConfig.ifCols);
		List<CamelListMap> listParent = (List<CamelListMap>) resultMap.get(parent);
		
		for(Map<String, Object> mapParent : listParent) {
	    	if(!isMatched(metaAttrs, mapSelf, mapParent))
	    		continue;
	    	
	    	List<Map> list = (List<Map>) mapParent.get(path);
    		if(list==null) {
    			list = new ArrayList<Map>();
    			mapParent.put(path, list);
    		}
    		
    		list.add(mapSelf);
    		return;
		} //for mapParent...
	}
	
	private boolean isMatched(List<Map> metaAttrs, Map mapSelf, Map<String, Object> mapParent) {
		boolean matched = false;
		
    	for(Map metaAttr : metaAttrs) {
    		if(metaAttr.get(Constants.parentCol) == null)
    			continue;
    		String parentCol = CamelListMap.toCamelCaseString(metaAttr.get(Constants.parentCol).toString());
    		
    		Object parentColData = mapParent.get(parentCol);
    		if(parentColData == null)
    			continue;
    		
    		if(metaAttr.get(Constants.ifCol) == null)
    			continue;
    		String selfCol = metaAttr.get(Constants.ifCol).toString();
    		
    		Object selfColData = mapSelf.get(selfCol);
    		if(selfColData == null)
    			continue;
    		
			if( selfColData.equals(parentColData) ) 
				matched = true; 
			else 
				return false; 
    	} //for metaAttr...
    	
    	return matched;
	}
}
