/*
 * Copyright (C) 2014, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * Symbolic Pathfinder (jpf-symbc) is licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0. 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package concolic;

import gov.nasa.jpf.symbc.Concrete;
import java.lang.Math;
public class TestConcreteAnote {

	@Concrete("true")
	public static void runSymbolic(int x, int y, String z) {
		if(x > y) {
			Math.sin(y);
			Math.min(x, y);
//			System.out.println("x > y");
		} else {
			Math.cos(x);
			Math.min(x, y);
//			if (z.length() < 6) {
//				System.out.println(z);
//			}
			System.out.println(z);
		}
		// check if the normal statement works
	}
	
	public static void main(String[] args) {
		runSymbolic(1, 2, "z");
	}
}