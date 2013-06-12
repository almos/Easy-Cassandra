/*
 * Copyright 2012 Otávio Gonçalves de Santana (otaviojava)
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.easycassandra.persistence;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cassandra.thrift.Cassandra.Client;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.Compression;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.CqlResult;
import org.apache.cassandra.thrift.CqlRow;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.easycassandra.ConsistencyLevelCQL;
import org.easycassandra.EasyCassandraException;
import org.easycassandra.util.EncodingUtil;
import org.easycassandra.util.ReflectionUtil;

/**
 * main Class for persistence The Objects
 *
 * @author otaviojava - otaviojava@java.net
 */

@SuppressWarnings({"rawtypes"})
public abstract class Persistence extends BasePersistence {

    private static final String MESSAGEM_NOT_EXIST_COLUMN_FAMILY = "not exist column Family, now try make it";
	private static final String MESSAGE_VERIFY_IF_EXIST_COLUMN_FAMILY = "Verify if exist and trying create the Column Family";
	private static final String FOR_LIST_BY_INDEX = " for list by index ";
	private static final String MESSAGE_USE_ANNOTATION = "You must use annotation @org.easycassandra.annotations.IndexValue in some field of the Class:";
	private static final String INDEX_CREATED_WITH_SUCCESS = "Index created with success, now trying execute the command again";
	private static final String COLUMN_FAMILY_CREATED_WITH_SUCCESS = "Column Family created with success, now trying execute the command again";
	private static final String MESSAGE_COLUMN_FAMILY_CREATED = COLUMN_FAMILY_CREATED_WITH_SUCCESS;
	private static final String DEFAULT_INDEX_NAME = "default";
	private static final int HASH = 11;
	private static final int HASH_VALUE = 5;
	/**
     * the value default for list the result
     */
    private static final int DEFAULT_VALUE = 10000;
   

    /**
     * Constructor for Persistence
     *
     * @param client
     * @param superColumnsRef
     * @param connectionInformation
     */
    Persistence(AtomicReference<ColumnFamilyIds> superColumnsRef,ConnectionInformation connectionInformation) {
        setReferenceSupercolumn(superColumnsRef);
        setConnectionInformation(connectionInformation);
        Thread thread = new Thread(new WriteDocument(superColumnsRef));
        thread.setDaemon(true);
        thread.start();
        
    }
    
    protected <T> List<T> retriveObject(String condiction, String condictionValue, Class<T> persistenceClass, ConsistencyLevelCQL consistencyLevel, int limit, IndexColumnName... index) {
        return retriveObject(condiction, condictionValue, persistenceClass,consistencyLevel, limit, " =", index);
    }

    /**
     * method for retrieve Objects
     *
     * @param condiction -condiction for search the Objects can be index and
     * rowkey default 10000
     * @param condictionValue - value for seach in condiction
     * @param persistenceClass - type object class
     * @param consistLevel level consistency for retrive the information
     * @param limit length max the list
     * @param keys - varargs for the index names
     * @return the list retrieved with length (limit)
     *
     */
    protected <T> List<T> retriveObject(String condiction, String condictionValue, Class<T> persistenceClass, ConsistencyLevelCQL consistLevel, int limit, String condicionValue, IndexColumnName... index) {
    	try {
    		
            StringBuilder cql = new StringBuilder();
            cql.append("select * from ");
            cql.append(ColumnUtil.getColumnFamilyName(persistenceClass));
            cql.append(" USING ").append(consistLevel.getValue()).append(" ");  
            cql.append(" where ");
            cql.append(" ").append(condiction).append(condicionValue);
            cql.append(condictionValue);
            cql.append(" LIMIT ").append(limit);
            CqlResult executeCqlQuery = executeCommandCQL(cql.toString(),getClient(persistenceClass),index);
            return listbyQuery(executeCqlQuery, persistenceClass);
        } catch (Exception exception) {
            Logger.getLogger(Persistence.class.getName()).log(Level.SEVERE, null, exception);
            return new ArrayList<T>();
        }
    }

    //insert commands
    /**
     * the method for insert the Object
     *
     * @param object - the Object for be insert in Cassandra the default of
     * consistency Level is ONE (ConsistencyLevel.ONE)
     * @return the result of insertion
     * @see #insert(java.lang.Object,
     * org.apache.cassandra.thrift.ConsistencyLevel)
     */
    public boolean insert(Object object) {
        return insert(object, ConsistencyLevel.ONE);
    }

    /**
     * the method for insert the Object
     *
     * @param object - the Object for be insert in Cassandra
     * @param consistencyLevel - Level consistency for be insering in Thrift
     * @return the result of insertion
     */
    public boolean insert(Object object, ConsistencyLevel consistencyLevel) {
        return insert(object, consistencyLevel, true);
    }

    /**
     * the method for insert the Object
     *
     * @param object - the Object for be insert in Cassandra
     * @param consistencyLevel -Level consistency for be insering in Thrift
     * @param autoEnable - if use the auto_increment or not
     * @return the result of insertion
     */
    private boolean insert(Object object, ConsistencyLevel consistencyLevel,boolean autoEnable) {

    	Client client=getClient(object.getClass());
    	try {
        	
            ByteBuffer rowid = getKey(object, autoEnable);
            ColumnParent columnParent = new ColumnParent(ColumnUtil.getColumnFamilyName(object.getClass()));
            for (Column column : ColumnUtil.getColumns(object)) {
                client.insert(rowid, columnParent, column, consistencyLevel);
            }
        } catch (Exception exception) {
            Logger logger = Logger.getLogger(Persistence.class.getName());
            if (createColumnFamily(exception, logger)) {
            	logger.log(Level.INFO, MESSAGE_COLUMN_FAMILY_CREATED);
                return insert(object, consistencyLevel, autoEnable);
            }
            Logger.getLogger(Persistence.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
        return true;
    }

	private Client getClient(Class object) {
		
		String keySpaceActual=getKeySpace(object);
    	boolean isNotkeySpaceEquals=!getKeySpace().equals(keySpaceActual);
    	if(isNotkeySpaceEquals){
    		return EasyCassandraManager.getClient(keySpaceActual, information().getHost(), information().getPort());
    	}else{
    		return getClient();
    	}
	}
    
    /**
     * checks whether this object has a specific keyspace or default
     * @param object
     * @return the specific keyspace or default
     */
	private String getKeySpace(Class<?> object) {
		return ColumnUtil.getSchema(object)==null?getKeySpace():ColumnUtil.getSchema(object);
	}

    /**
     * the method for insert the Object
     *
     * @param object - the Object for be insert in Cassandra
     * @param consistencyLevel - Level consistency for be insering
     * @return the result of insertion
     */
    public boolean insert(Object object, ConsistencyLevelCQL consistencyLevel) {
        return insert(object, consistencyLevel.toConsistencyLevel(), true);
    }

    /**
     * the method for insert the Object
     *
     * @param object - the Object for be insert in Cassandra
     * @param consistLevel -Level consistency for be insering
     * @param autoEnable - if use the auto_increment or not
     * @return the result of insertion
     */
    public boolean insert(Object object, ConsistencyLevelCQL consistLevel, boolean autoEnable) {
        return insert(object, consistLevel.toConsistencyLevel(), autoEnable);
    }

    /**
     * The method for retrieve values from Query command
     *
     * @param query - Cassandra Query Language
     * @return The values from query like List of the Map<String, String> Each
     * List is a row Each Map<String,String>: a key in the map is equal a
     * column's name and the value in the map is the column's value.
     *
     */
    public List<Map<String, String>> executeCql(String query) {
        CqlResult resultCQL = executeCommandCQL(query,getClient());
        List<Map<String, String>> listMap = new ArrayList<Map<String, String>>();

        for (CqlRow row : resultCQL.rows) {
            Map<String, String> mapColumn = new HashMap<String, String>();

            for (Column cl : row.getColumns()) {

                mapColumn.put(EncodingUtil.byteToString(cl.name),
                        EncodingUtil.byteToString(cl.value));
            }
            listMap.add(mapColumn);
        }
        return listMap;
    }

    /**
     * CQL executes and returns if there was success or not
     *
     * @param query - Cassandra Query Language
     * @return - if there was sucess return true otherwise false
     */
    public boolean executeUpdateCql(String query) {
        return executeCommandCQL(query,getClient()) != null;
    }

    /**
     * @param cql - Cassandra Query Language
     * @return the result executing query
     * @param the name of index
     */
    CqlResult executeCommandCQL(String cql,Client client, IndexColumnName... index) {
        try {
            return getClient().execute_cql_query(ByteBuffer.wrap(cql.getBytes()),Compression.NONE);
        } catch (Exception exception) {
            Logger logger = Logger.getLogger(Persistence.class.getName());


            if (createColumnFamily(exception, logger)) {
                logger.log(Level.WARNING, COLUMN_FAMILY_CREATED_WITH_SUCCESS);
                return executeCommandCQL(cql,client, index);
            } else if (createIndex(exception, logger, index)) {
                logger.log(Level.WARNING, INDEX_CREATED_WITH_SUCCESS);
                return executeCommandCQL(cql,client);
            } else {
                logger.log(Level.SEVERE, null, exception);
            }
        }
        return null;
    }

    /**
     * the method for retrieve the object in Cassandra
     *
     * @param persistenceClass - type of class for be retrieve the default of
     * consistency Level is ONE (ConsistencyLevel.ONE) The default lenght list
     * is 10.000
     * @see #findAll(java.lang.Class, org.easycassandra.ConsistencyLevelCQL,
     * int)
     * @return the list with Object is retrive
     */
    public <T> List<T> findAll(Class<T> persistenceClass) {
        return findAll(persistenceClass, ConsistencyLevelCQL.ONE, DEFAULT_VALUE);
    }

    /**
     * the method for retrieve the object in Cassandra
     *
     * @param persistenceClass - type of class for be retrieve
     * @param limit - lenght the list The default of consistency Level is ONE
     * (ConsistencyLevel.ONE)
     * @see #findAll(java.lang.Class, org.easycassandra.ConsistencyLevelCQL,
     * int)
     * @return the list with Object is retrive
     */
    public <T> List<T> findAll(Class<T> persistenceClass, int limit) {
        return findAll(persistenceClass, ConsistencyLevelCQL.ONE, limit);
    }

    /**
     * the method for retrieve the object in Cassandra
     *
     * @param persistClass - type of class for be retrieve
     * @param consistencyLevel - Level of consitency for retrive the Object
     * @see #findAll(java.lang.Class, org.easycassandra.ConsistencyLevelCQL,
     * int)
     * @return the list with Object is retrive
     */
    public <T> List<T> findAll(Class<T> persistClass, ConsistencyLevelCQL consistLevel) {
        return findAll(persistClass, consistLevel, DEFAULT_VALUE);
    }

    /**
     * the method for retrieve the object in Cassandra
     *default 10000
     * @param persistClass - type of class for be retrieve
     * @param consistLevel - Level of consitency for retrive the Object
     * @param limit - lenght the list
     * @return the list with Object is retrive
     */
    
	public <T> List<T> findAll(Class<T> persistClass, ConsistencyLevelCQL consistLevel,int limit) {
        try {
            StringBuilder cql = new StringBuilder();
            cql.append(" select  * ");
            cql.append(" from ");
            cql.append(ColumnUtil.getColumnFamilyName(persistClass));
            cql.append(" USING ").append(consistLevel.getValue()).append(" ");   
            cql.append("LIMIT ").append(limit);
            CqlResult executeCqlQuery = executeCommandCQL(cql.toString(),getClient(persistClass));
            return listbyQuery(executeCqlQuery, persistClass);
        } catch (Exception exception) {
            Logger.getLogger(Persistence.class.getName()).log(Level.SEVERE,
                    null, exception);
        }
        return new ArrayList<T>();
    }

    /**
     * The method for retrive the object from the result of Cassandra Query
     * Language
     *
     * @param resultCQL - The result of Cassandra Query Language
     * @param persistenceClass - The kind for retrieve the Class
     * @return The result of List
     * @throws NumberFormatException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public <T> List<T> listbyQuery(CqlResult resultCQL, Class<T> persistenceClass)  throws InstantiationException, IllegalAccessException {
        List<Map<String, ByteBuffer>> listMap = new ArrayList<Map<String, ByteBuffer>>();

        for (CqlRow row : resultCQL.rows) {
            Map<String, ByteBuffer> mapColumn = new HashMap<String, ByteBuffer>();

            for (Column column : row.getColumns()) {
                if (column.timestamp != -1) {
                    mapColumn.put("TIMESTAMP", EncodingUtil.stringToByte(
                            String.valueOf(column.timestamp)));
                }
                mapColumn.put(EncodingUtil.byteToString(column.name),
                        column.value);

            }
            listMap.add(mapColumn);
        }

        return getList(listMap, persistenceClass);

    }

    /**
     * The method for retrieve a object from rowkey
     *
     * @param key - The value of rowkey
     * @param persistenceClass - The kind class The default of consistency Level
     * is ONE (ConsistencyLevel.ONE)
     * @return - The object from key
     * @see #findByKey(java.lang.Object, java.lang.Class,
     * org.easycassandra.ConsistencyLevelCQL)
     */
    
	public <T> T findByKey(Object key, Class<T> persistenceClass) {
        return (T)findByKey(key, persistenceClass, ConsistencyLevelCQL.ONE);
    }

    /**
     * The method for retrieve a object from rowkey
     *
     * @param key - The value of rowkey
     * @param persistenceClass - The kind class
     * @param consistencyLevel - The consistency Level
     * @return - The object from key
     */
	public <T> T findByKey(Object key, Class<T> persistenceClass,ConsistencyLevelCQL consistencyLevel) {
        int limit = 1;
        String keyString = "'" + EncodingUtil.byteToString(Persistence.getWriteManager().convert(key)) + "'";
        String condicion = "KEY";
        List<T> objects = retriveObject(condicion, keyString, persistenceClass,consistencyLevel, limit);
        if (objects.size() > 0) {
            return   objects.get(0);
        }
        return null;
    }

    /**
     * The method for retrieve a list of object from in rowkey
     *
     * @param baseClass - The kind class
     * @param keys - the values of rowkey
     * @return - List with object in this method use the ConsistencyLevelCQL
     * like ConsistencyLevelCQL.ONE
     * @see Persistence#findByKeyIn(Class, ConsistencyLevelCQL, Object...)
     */
    public <T> List<T> findByKeyIn(Class<T> baseClass, Object... keys) {
        return findByKeyIn(baseClass, ConsistencyLevelCQL.ONE, keys);
    }

    /**
     * The method for retrieve a list of object from in rowkey
     *
     * @param baseClass - The kind class
     * @param consistencyLevel - The consistency Level
     * @param keys - the values of rowkey
     * @return - List with object in
     */
    public <T> List<T> findByKeyIn(Class<T> baseClass, ConsistencyLevelCQL consistLevel, Object... keys) {
        StringBuilder keyNames = new StringBuilder();
        keyNames.append(" (");
        String condicion = "";
        for (Object key : keys) {
            keyNames.append(" ");
            keyNames.append(condicion);
            keyNames.append("'");
            keyNames.append(EncodingUtil.byteToString(
                    Persistence.getWriteManager().convert(key))).append("'");
            condicion = ",";
        }
        return retriveObject("KEY", keyNames.substring(0, keyNames.length())+ ") ", baseClass, consistLevel, keys.length, " in ");
    }

    //delete comand
    /**
     * Delete the Object from the key value
     *
     * @param keyValue - The key value
     * @param objectClass - The Kind of class
     * @return the result of deletion
     */
    public boolean deleteByKeyValue(Object keyValue, Class objectClass) {
        String keyString = EncodingUtil.byteToString(
                Persistence.getWriteManager().convert(keyValue));
        return runDeleteCqlCommand(keyString, objectClass);
    }

    /**
     * Delete the Object from the key value
     *
     * @param keyObject - The Object for be delete
     * @return the result of deletion
     */
    public boolean delete(Object keyObject) {
        Field keyField = ColumnUtil.getKeyField(keyObject.getClass());
        String keyString = EncodingUtil.byteToString(
                Persistence.getWriteManager().convert(ReflectionUtil.getMethod(
                keyObject, keyField)));
        return runDeleteCqlCommand(keyString, keyObject.getClass());
    }

    /**
     * Create the cql and remove the Object
     *
     * @param keyValue - The value for row key
     * @param persistenceClass - The kind object
     * @return the result of deletion
     */
    protected boolean runDeleteCqlCommand(String keyValue, Class persistClass) {
        StringBuilder cql = new StringBuilder();
        cql.append("delete ");
        cql.append(" from ");
        cql.append(ColumnUtil.getColumnFamilyName(persistClass));
        cql.append(" where KEY = '");
        cql.append(keyValue);
        cql.append("'");
        CqlResult cqlResult = executeCommandCQL(cql.toString(),getClient(persistClass));
        return cqlResult != null;
    }

    //find index
    /**
     * Find list objects from index
     *
     * @param index - the index value
     * @param objectClass - Kind the Object the default of consistency Level is
     * ONE (ConsistencyLevel.ONE) The default lenght list is 10.000
     * @see #findByIndex(java.lang.Object, java.lang.Class,
     * org.easycassandra.ConsistencyLevelCQL, int)
     * @return list retrieve from the value index
     *
     */
    public <T> List<T> findByIndex(Object index, Class<T> objectClass) {
        return findByIndex(index, DEFAULT_INDEX_NAME, objectClass, ConsistencyLevelCQL.ONE);
    }
    
    public <T> List<T> findByIndex(Object indexValue, String indexName, Class<T> objectClass) {
        return findByIndex(indexValue, indexName, objectClass, ConsistencyLevelCQL.ONE);
    }

    /**
     * Find list objects from index
     *
     * @param index - the index value
     * @param objectClass - Kind the Object
     * @param consistencyLevel - The consistency Level
     * @see #findByIndex(java.lang.Object, java.lang.Class,
     * org.easycassandra.ConsistencyLevelCQL, int) The default length list is
     * 10.000
     * @return list retrieve from the value index
     */
    public <T>  List<T> findByIndex(Object index, String indexName, Class<T> objectClass,ConsistencyLevelCQL consistencyLevel) {
        return findByIndex(index, indexName, objectClass, consistencyLevel, DEFAULT_VALUE);
    }

    /**
     * Find list objects from index
     *
     * @param index - the index value
     * @param objectClass - kind the Object
     * @param consistencyLevelCQL - The consistency Level
     * @param limit - The length of List
     * @return list retrieve from the value index
     */
    public <T> List<T> findByIndex(Object index, String indexName, Class<T> objectClass,ConsistencyLevelCQL consistencyLevelCQL, int limit) {
        String indexString = "'" + EncodingUtil.byteToString(Persistence.getWriteManager().convert(index)) + "'";
        Field indexField = ColumnUtil.getIndexField(indexName, objectClass);
        if (indexField == null) {
            try {
                throw new EasyCassandraException(MESSAGE_USE_ANNOTATION + objectClass.getName()+ FOR_LIST_BY_INDEX);
            } catch (EasyCassandraException exception) {
                Logger.getLogger(Persistence.class.getName()).log(Level.SEVERE,null, exception);
                return null;
            }
        }
        String condicao = ColumnUtil.getColumnName(indexField);
        IndexColumnName indexColumnName = new IndexColumnName();
        indexColumnName.setColumnFamily(ColumnUtil.getColumnFamilyName(objectClass));
        indexColumnName.setIndex(condicao);
        return retriveObject(condicao, indexString, objectClass,consistencyLevelCQL, limit, indexColumnName);
    }

    /**
     * Update the Object The default of consistency Level is ONE
     * (ConsistencyLevel.ONE)
     *
     * @see #update(java.lang.Object, org.easycassandra.ConsistencyLevelCQL)
     * @param object = The Object will updated
     * @return the result of update data
     */
    public boolean update(Object object) {
        return update(object, ConsistencyLevelCQL.ONE);
    }

    /**
     * Update the Object
     *
     * @param object - The Object will updated
     * @param consistencyLevel - Th consistency Level
     * @return the result of update data
     */
    public boolean update(Object object, ConsistencyLevelCQL consistencyLevel) {
        return insert(object, consistencyLevel, true);
    }

    /**
     * Return the number of the rows in Column Family
     *
     * @param clazz - class for retrieve the column Family
     * @return the number of the rows
     */
    public Long count(Class<?> clazz) {
        return count(clazz, ConsistencyLevelCQL.ONE);
    }

    /**
     * Return the number of the rows in Column Family
     *
     * @param clazz - class for retrieve the column Family
     * @param consistencyLevel - Consistency Level
     * @return the number of the rows
     */
    public Long count(Class<?> clazz, ConsistencyLevelCQL consistencyLevel) {
        StringBuilder cql = new StringBuilder();
        cql.append("SELECT count(*) ");
        cql.append(" from ");
        cql.append(ColumnUtil.getColumnFamilyName(clazz));
        cql.append(" USING ").append(consistencyLevel.getValue()).append(" ");   
        CqlResult cqlResult = executeCommandCQL(cql.toString(),getClient(clazz));
        return cqlResult.rows.get(0).getColumns().get(0).value.asLongBuffer().get();
    }

    // treatment exception
    /**
     * checks whether the problem is the ColumnFamily does not exist and tries
     * to create
     *
     * @param exception - the problem
     * @param logger - to leave in log the problem
     * @return if created the column with success
     */
    private boolean createColumnFamily(Exception exception, Logger logger) {
        if (exception instanceof InvalidRequestException  && ((InvalidRequestException) exception).getWhy().contains(
                "unconfigured columnfamily ")) {
            logger.log(Level.WARNING,MESSAGE_VERIFY_IF_EXIST_COLUMN_FAMILY);
            logger.log(Level.INFO, MESSAGEM_NOT_EXIST_COLUMN_FAMILY);
            String columnFamily = ((InvalidRequestException) exception).getWhy().replace("unconfigured columnfamily ", "");
            String query = " CREATE COLUMNFAMILY " + columnFamily + " (KEY text PRIMARY KEY); ";
            return executeCommandCQL(query,getClient()) != null;
        }
        return false;
    }

    /**
     * checks whether the problem is the Index does not exist and tries to
     * create
     *
     * @param exception - the problem
     * @param logger - to leave in log the problem
     * @param indexs - information about the index
     * @return if created the index with success
     */
    private boolean createIndex(Exception exception, Logger logger,IndexColumnName... indexs) {
        if (exception instanceof InvalidRequestException
                && ((InvalidRequestException) exception).getWhy().contains(
                "No indexed columns present in by-columns clause ")) {
            logger.log(Level.INFO, "not exist index, now try make it");
            for (IndexColumnName index : indexs) {
                StringBuilder query = new StringBuilder();
                logger.log(Level.INFO, "Try create column");
                query.append(" ALTER TABLE ");
                query.append(index.getColumnFamily());
                query.append(" ADD ");
                query.append(index.getIndex());
                query.append(" text ");
                if (executeCommandCQL(query.toString(),getClient()) == null) {
                    logger.log(Level.INFO, "The column with name{0} exists",
                            index.getIndex());
                }
                query = new StringBuilder();
                query.append(" CREATE INDEX   ");
                query.append(index.getIndex().concat("_key"));
                query.append("  ON   ");
                query.append(index.getColumnFamily());
                query.append(" ( ");
                query.append(index.getIndex());
                query.append(" )");
                logger.log(Level.INFO, "run the cql: {0}", query.toString());
                if (executeCommandCQL(query.toString(),getClient()) == null) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * get the Cassandra's Client used in the Class
     *
     * @return
     */
    public abstract Client getClient();
    
    

    /**
     * Class for Store information about the index and the Column name
     *
     * @author otavio
     *
     */
    class IndexColumnName {

        /**
         * name of index
         */
        private String index;
        /**
         * name of column Family
         */
        private String columnFamily;

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public String getColumnFamily() {
            return columnFamily;
        }

        public void setColumnFamily(String columnFamily) {
            this.columnFamily = columnFamily;
        }
    }

    /**
     * Create the JCassandra
     *
     * @param cql
     * @return
     */
    public JCassandra createJCassandra(String cql) {
        JCassandraImpl jCassandra = null;
        try {
            jCassandra = new JCassandraImpl(cql);
            jCassandra.setPersistence(this);
        } catch (EasyCassandraException exception) {
            Logger.getLogger(Persistence.class.getName()).log(Level.WARNING,
                    "Column Family created with success, now trying execute "
                    + "the command again");
        }
        return jCassandra;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Persistence other = (Persistence) obj;
        if ((this.getKeySpace() == null) ? (other.getKeySpace() != null) : !this.getKeySpace().equals(other.getKeySpace())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        
        return HASH * HASH_VALUE + (this.getKeySpace() != null ? this.getKeySpace().hashCode() : 0);
    
    }
    
    /**
	 * Return the number of connections in this client
	 * @return number of connection
	 */
	public abstract int size();
    
    
}
