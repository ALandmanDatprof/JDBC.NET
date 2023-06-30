package com.chequer.jdbcnet.bridge.service;

import com.chequer.jdbcnet.bridge.manager.ObjectManager;
import com.chequer.jdbcnet.bridge.models.ResultSetEx;
import com.chequer.jdbcnet.bridge.utils.Utils;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import proto.Common;
import proto.statement.Statement;
import proto.statement.StatementServiceGrpc;

import java.sql.*;

public class StatementServiceImpl extends StatementServiceGrpc.StatementServiceImplBase {

    @Override
    public void prepareStatement(Statement.PrepareStatementRequest request, StreamObserver<Statement.PrepareStatementResponse> responseObserver) {
        try {
            var connection = ObjectManager.getConnection(request.getConnectionId());
            var statement = connection.prepareStatement(request.getSql(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            var statementId = ObjectManager.putStatement(statement);

            var response = Statement.PrepareStatementResponse.newBuilder()
                    .setStatementId(statementId)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void createStatement(Statement.CreateStatementRequest request, StreamObserver<Statement.CreateStatementResponse> responseObserver) {
        try {
            var connection = ObjectManager.getConnection(request.getConnectionId());
            var statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            var statementId = ObjectManager.putStatement(statement);

            var response = Statement.CreateStatementResponse.newBuilder()
                    .setStatementId(statementId)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void executeStatement(Statement.ExecuteStatementRequest request, StreamObserver<Common.JdbcResultSetResponse> responseObserver) {
        try {
            var statement = ObjectManager.getStatement(request.getStatementId());
            statement.setFetchSize(request.getFetchSize() == -1 ? statement.getMaxRows() : request.getFetchSize());

            boolean result;
            if (statement instanceof PreparedStatement) {
                var preparedStatement = (PreparedStatement)statement;
                result = preparedStatement.execute();
            }
            else {
                result = statement.execute(request.getSql());
            }

            if (!result) {
                var responseBuilder = Common.JdbcResultSetResponse.newBuilder()
                        .setRecordsAffected(statement.getUpdateCount());

                responseObserver.onNext(responseBuilder.build());
            } else {
                var metaData = statement.getResultSet().getMetaData();
                var resultSet = new ResultSetEx(statement.getResultSet());

                var resultSetId = ObjectManager.putResultSet(resultSet);

                var responseBuilder = Common.JdbcResultSetResponse.newBuilder()
                        .setResultSetId(resultSetId)
                        .setHasRows(resultSet.getHasRows());

                Utils.addColumns(responseBuilder, metaData);
                responseObserver.onNext(responseBuilder.build());
            }

            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void cancelStatement(Statement.CancelStatementRequest request, StreamObserver<Empty> responseObserver) {
        try {
            var statement = ObjectManager.getStatement(request.getStatementId());
            statement.cancel();

            var response = Empty.newBuilder()
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void closeStatement(Statement.CloseStatementRequest request, StreamObserver<Empty> responseObserver) {
        try {
            var statement = ObjectManager.getStatement(request.getStatementId());
            statement.close();

            ObjectManager.removeStatement(request.getStatementId());

            var response = Empty.newBuilder()
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void setParameter(Statement.SetParameterRequest request, StreamObserver<Empty> responseObserver) {
        try {
            var statement = ObjectManager.getStatement(request.getStatementId());

            if (!(statement instanceof PreparedStatement))
                throw new Exception("Must be prepared");

            var preparedStatement = (PreparedStatement)statement;

            var index = request.getIndex();
            var value = request.getValue();

            switch (request.getType()) {
                case INT:
                    preparedStatement.setInt(index, Integer.parseInt(value));
                    break;

                case LONG:
                    preparedStatement.setLong(index, Long.parseLong(value));
                    break;

                case SHORT:
                    preparedStatement.setShort(index, Short.parseShort(value));
                    break;

                case FLOAT:
                    preparedStatement.setFloat(index, Float.parseFloat(value));
                    break;

                case DOUBLE:
                    preparedStatement.setDouble(index, Double.parseDouble(value));
                    break;

                case STRING:
                    preparedStatement.setString(index, value);
                    break;

                case BOOLEAN:
                    preparedStatement.setBoolean(index, Boolean.parseBoolean(value));
                    break;

                case TIME:
                    preparedStatement.setTime(index, Time.valueOf(value));
                    break;

                case DATE:
                    preparedStatement.setDate(index, Date.valueOf(value));
                    break;
            }

            var response = Empty.newBuilder()
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }
}
