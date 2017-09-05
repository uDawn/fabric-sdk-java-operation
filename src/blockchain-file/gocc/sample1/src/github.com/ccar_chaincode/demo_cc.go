/*
Copyright IBM Corp. 2016 All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

		 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package main

//WARNING - this chaincode's ID is hard-coded in chaincode_example04 to illustrate one way of
//calling chaincode from a chaincode. If this example is modified, chaincode_example04.go has
//to be modified as well with the new ID of chaincode_example02.
//chaincode_example05 show's how chaincode ID can be passed in as a parameter instead of
//hard-coding.

import (
	"encoding/binary"
	"fmt"
	"math"
	"strconv"

	"github.com/hyperledger/fabric/core/chaincode/shim"
	pb "github.com/hyperledger/fabric/protos/peer"
	"encoding/json"
	"time"
)

// SimpleChaincode example simple Chaincode implementation
type SimpleChaincode struct {
}

func float2bytearr(f float64) []byte {
	//	var buf [8]byte
	//	n := math.Float64bits(f)
	//	buf[0] = byte(n >> 56)
	//	buf[1] = byte(n >> 48)
	//	buf[2] = byte(n >> 40)
	//	buf[3] = byte(n >> 32)
	//	buf[4] = byte(n >> 24)
	//	buf[5] = byte(n >> 16)
	//	buf[6] = byte(n >> 8)
	//	buf[7] = byte(n)
	//	return []byte(buf)
	bits := math.Float64bits(f)
	bytes := make([]byte, 8)
	binary.BigEndian.PutUint64(bytes, bits)

	return bytes
}

func bytearr2float(bytes []byte) float64 {
	bits := binary.BigEndian.Uint64(bytes)
	float := math.Float64frombits(bits)
	return float
}

func float2string(inputNum float64) string {
	// to convert a float number to a string
	return strconv.FormatFloat(inputNum, 'f', 6, 64)
}

func (t *SimpleChaincode) Init(stub shim.ChaincodeStubInterface) pb.Response {
	fmt.Println("ex02 Init")
	_, args := stub.GetFunctionAndParameters()
	var A, B string        // Entities
	var Aval, Bval float64 // Asset holdings
	var err error

	if len(args) != 4 {
		return shim.Error("Incorrect number of arguments. Expecting 4")
	}

	// Initialize the chaincode
	A = args[0]
	Aval, err = strconv.ParseFloat(args[1], 64)
	if err != nil {
		return shim.Error("Expecting integer value for asset holding")
	}
	B = args[2]
	Bval, err = strconv.ParseFloat(args[3], 64)
	if err != nil {
		return shim.Error("Expecting integer value for asset holding")
	}
	fmt.Printf("Aval = %f, Bval = %f\n", Aval, Bval)

	// Write the state to the ledger

	err = stub.PutState(A, float2bytearr(Aval))
	if err != nil {
		return shim.Error(err.Error())
	}

	// err = stub.PutState(B, []byte(strconv.Itoa(Bval)))
	err = stub.PutState(B, float2bytearr(Bval))
	if err != nil {
		return shim.Error(err.Error())
	}

	return shim.Success(nil)
}

func (t *SimpleChaincode) Invoke(stub shim.ChaincodeStubInterface) pb.Response {
	fmt.Println("ex02 Invoke")
	function, args := stub.GetFunctionAndParameters()
	if function == "invoke" {
		// Make payment of X units from A to B
		return t.invoke(stub, args)
	} else if function == "delete" {
		// Deletes an entity from its state
		return t.delete(stub, args)
	} else if function == "query" {
		// the old "Query" is now implemtned in invoke
		return t.query(stub, args)
	} else if function == "give" {
		return t.give(stub, args)
	} else if function == "transfer" {
		return t.transfer(stub, args)
	} else if function == "queryHistory" {
		return t.queryHistory(stub, args)
	}

	return shim.Error("Invalid invoke function name. Expecting \"invoke\" \"delete\" \"query\"")
}

type KeyModificationRecord struct {
	TxId      string
	Value     string
	Timestamp string
	IsDelete  bool
}

type KeyModiHistory struct {
	History []KeyModificationRecord
}

func (t *SimpleChaincode) queryHistory(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	if len(args) != 1 {
		return shim.Error("Incorrect number of arguments. Expecting 1")
	}
	var key string
	key = args[0]

	iter, err := stub.GetHistoryForKey(key)
	if err != nil {
		return shim.Error("Failed to get history")
	}

	if iter == nil {
		return shim.Error("History not found")
	}

	history := []KeyModificationRecord{}
	for ; iter.HasNext(); {
		keyModi, err := iter.Next()
		if err == nil {
			keyTxid := keyModi.TxId
			keyValue := keyModi.Value
			keyTimestamp := keyModi.Timestamp
			keyIsDeleted := keyModi.IsDelete

			modification := KeyModificationRecord{
				TxId:      keyTxid,
				Value:     float2string(bytearr2float(keyValue)),
				Timestamp: time.Unix(keyTimestamp.Seconds, int64(keyTimestamp.Nanos)).Format("2006-01-02 15:04:05"),
				IsDelete:  keyIsDeleted,
			}
			history = append(history, modification)
		}
	}

	keyModiHistory := KeyModiHistory{
		History: history,
	}

	objkeyModiHistory, _ := json.Marshal(keyModiHistory)

	return shim.Success(objkeyModiHistory)
}

func (t *SimpleChaincode) transfer(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var A, B string        // Entities
	var Aval, Bval float64 // Asset holdings
	var X float64          // Transaction value
	var err error

	if len(args) != 3 {
		return shim.Error("Incorrect number of arguments. Expecting 3")
	}

	A = args[0]
	B = args[1]

	// Get the state from the ledger
	// TODO: will be nice to have a GetAllState call to ledger

	Avalbytes, err := stub.GetState(A)
	if err != nil {
		return shim.Error("Failed to get state")
	}
	if Avalbytes == nil {
		return shim.Error("Entity not found")
	}
	// Aval, _ = strconv.ParseFloat(string(Avalbytes), 64)
	Aval = bytearr2float(Avalbytes)

	Bvalbytes, err := stub.GetState(B)
	if err != nil {
		return shim.Error("Failed to get state")
	}
	if Bvalbytes == nil {
		return shim.Error("Entity not found")
	}
	// Bval, _ = strconv.ParseFloat(string(Bvalbytes), 64)
	Bval = bytearr2float(Bvalbytes)

	// Perform the execution
	X, err = strconv.ParseFloat(args[2], 64)
	if err != nil {
		return shim.Error("Invalid transaction amount, expecting a float value")
	}
	Aval = Aval - X
	Bval = Bval + X
	fmt.Printf("Aval = %f, Bval = %f\n", Aval, Bval)

	// Write the state back to the ledger
	err = stub.PutState(A, float2bytearr(Aval))
	if err != nil {
		return shim.Error(err.Error())
	}

	err = stub.PutState(B, float2bytearr(Bval))
	if err != nil {
		return shim.Error(err.Error())
	}

	return shim.Success(nil)
}

// Transaction makes payment of X units from A to B
func (t *SimpleChaincode) give(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var countName string
	var X, Aval float64
	var err error
	if len(args) != 2 {
		return shim.Error("Incorrect number of arguments. Expecting 3")
	}
	countName = args[0]
	X, err = strconv.ParseFloat(args[1], 64)
	if err != nil {
		return shim.Error("Invalid transaction amount, expecting a float value")
	}
	Avalbytes, err := stub.GetState(countName)
	if err != nil {
		return shim.Error("Failed to get state")
	}
	if Avalbytes == nil {
		err = stub.PutState(countName, float2bytearr(X))
		if err != nil {
			return shim.Error(err.Error())
		}
		return shim.Success(nil)
	}
	// Aval, _ = strconv.ParseFloat(string(Avalbytes), 64)
	Aval = bytearr2float(Avalbytes)
	Aval = Aval + X
	err = stub.PutState(countName, float2bytearr(Aval))
	if err != nil {
		return shim.Error(err.Error())
	}
	return shim.Success(nil)

}
func (t *SimpleChaincode) invoke(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var A, B string        // Entities
	var Aval, Bval float64 // Asset holdings
	var X float64          // Transaction value
	var err error

	if len(args) != 3 {
		return shim.Error("Incorrect number of arguments. Expecting 3")
	}

	A = args[0]
	B = args[1]

	// Get the state from the ledger
	// TODO: will be nice to have a GetAllState call to ledger
	Avalbytes, err := stub.GetState(A)
	if err != nil {
		return shim.Error("Failed to get state")
	}
	if Avalbytes == nil {
		return shim.Error("Entity not found")
	}
	// Aval, _ = strconv.ParseFloat(string(Avalbytes), 64)
	Aval = bytearr2float(Avalbytes)

	Bvalbytes, err := stub.GetState(B)
	if err != nil {
		return shim.Error("Failed to get state")
	}
	if Bvalbytes == nil {
		return shim.Error("Entity not found")
	}
	// Bval, _ = strconv.ParseFloat(string(Bvalbytes), 64)
	Bval = bytearr2float(Bvalbytes)

	// Perform the execution
	X, err = strconv.ParseFloat(args[2], 64)
	if err != nil {
		return shim.Error("Invalid transaction amount, expecting a float value")
	}
	Aval = Aval - X
	Bval = Bval + X
	fmt.Printf("Aval = %f, Bval = %f\n", Aval, Bval)

	// Write the state back to the ledger
	err = stub.PutState(A, float2bytearr(Aval))
	if err != nil {
		return shim.Error(err.Error())
	}

	err = stub.PutState(B, float2bytearr(Bval))
	if err != nil {
		return shim.Error(err.Error())
	}

	return shim.Success(nil)
}

// Deletes an entity from state
func (t *SimpleChaincode) delete(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	if len(args) != 1 {
		return shim.Error("Incorrect number of arguments. Expecting 1")
	}

	A := args[0]

	// Delete the key from the state in ledger
	err := stub.DelState(A)
	if err != nil {
		return shim.Error("Failed to delete state")
	}

	return shim.Success(nil)
}

// query callback representing the query of a chaincode
func (t *SimpleChaincode) query(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var A string // Entities
	var err error

	if len(args) != 1 {
		return shim.Error("Incorrect number of arguments. Expecting name of the person to query")
	}

	A = args[0]

	// Get the state from the ledger
	Avalbytes, err := stub.GetState(A)
	if err != nil {
		jsonResp := "{\"Error\":\"Failed to get state for " + A + "\"}"
		return shim.Error(jsonResp)
	}

	if Avalbytes == nil {
		jsonResp := "{\"Error\":\"Nil amount for " + A + "\"}"
		return shim.Error(jsonResp)
	}

	jsonResp := "{\"Name\":\"" + A + "\",\"Amount\":\"" + string(Avalbytes) + "\"}"
	fmt.Printf("Query Response:%s\n", jsonResp)
	return shim.Success(Avalbytes)
}

func main() {
	err := shim.Start(new(SimpleChaincode))
	if err != nil {
		fmt.Printf("Error starting Simple chaincode: %s", err)
	}
}
